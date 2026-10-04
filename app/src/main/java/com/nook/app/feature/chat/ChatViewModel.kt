package com.nook.app.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.AppConfig
import com.nook.app.data.model.Chat
import com.nook.app.data.model.GiphyItem
import com.nook.app.data.model.Media
import com.nook.app.data.model.Message
import com.nook.app.data.model.MessageType
import com.nook.app.data.model.Presence
import com.nook.app.data.model.PrivateSettings
import com.nook.app.data.model.ReplyRef
import com.nook.app.data.model.SendStatus
import com.nook.app.data.model.Sticker
import com.nook.app.data.model.User
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.ChatRepository
import com.nook.app.data.repo.ConnectivityRepository
import com.nook.app.data.repo.LiveWindow
import com.nook.app.data.repo.MessageRepository
import com.nook.app.data.repo.OutgoingMessage
import com.nook.app.data.repo.Outbox
import com.nook.app.data.repo.Payload
import com.nook.app.data.repo.RealtimeRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.util.TimeFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ChatHeaderUi(
    val title: String = "",
    val photoUrl: String? = null,
    val isGroup: Boolean = false,
    val subtitle: String = "",
    val subtitleActive: Boolean = false,
    val otherUid: String? = null,
    val online: Boolean = false,
)

data class MessageUi(
    val message: Message,
    val mine: Boolean,
    val senderName: String?,
    val senderPhoto: String?,
    val showSender: Boolean,
    val joinPrev: Boolean,
    val joinNext: Boolean,
    val isNew: Boolean,
    val seen: Boolean,
    val seenLabel: String?,
    val replySenderName: String?,
    val reactionSummary: List<Pair<String, Int>>,
    val myReaction: String?,
)

sealed interface ChatItem {
    val key: String
    data class Msg(val ui: MessageUi) : ChatItem { override val key get() = ui.message.id }
    data class DateSeparator(val label: String, override val key: String) : ChatItem
}

data class ChatUiState(
    val loading: Boolean = true,
    val chat: Chat? = null,
    val header: ChatHeaderUi = ChatHeaderUi(),
    val items: List<ChatItem> = emptyList(),
    val typing: List<String> = emptyList(),
    val canLoadMore: Boolean = true,
    val loadingMore: Boolean = false,
    val blockedOther: Boolean = false,
    val notMember: Boolean = false,
    val error: String? = null,
)

/**
 * Message window = live listener on the newest page + older pages fetched on demand + messages
 * that fell out of the live window + the local outbox. Only messages that arrive AFTER the first
 * snapshot are flagged [MessageUi.isNew], so initial loads and older pages never animate in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    val chatId: String,
    private val chats: ChatRepository,
    private val messages: MessageRepository,
    private val users: UserRepository,
    private val realtime: RealtimeRepository,
    private val auth: AuthRepository,
    private val outbox: Outbox,
    connectivity: ConnectivityRepository,
) : ViewModel() {

    val me: String = auth.uid.orEmpty()
    val online = connectivity.online

    private val older = MutableStateFlow<List<Message>>(emptyList())
    private val retained = MutableStateFlow<Map<String, Message>>(emptyMap())
    private val newIds = MutableStateFlow<Set<String>>(emptySet())
    private val seenIds = HashSet<String>()
    private var firstSnapshot = true
    private var prevLive: Map<String, Message> = emptyMap()
    private val loadingMore = MutableStateFlow(false)
    private val canLoadMore = MutableStateFlow(true)
    private val _replyTo = MutableStateFlow<Message?>(null)
    val replyTo: StateFlow<Message?> = _replyTo.asStateFlow()
    private val now = flow { while (true) { emit(System.currentTimeMillis()); delay(10_000) } }

    private val chatFlow: StateFlow<Chat?> = chats.observeChat(chatId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val live: Flow<LiveWindow?> = messages.observeLatest(chatId)
        .onEach { onLiveSnapshot(it) }
        .map<LiveWindow, LiveWindow?> { it }
        .catch { emit(null) }

    private val settings: Flow<PrivateSettings> = users.observePrivateSettings()

    private val memberUsers: Flow<Map<String, User>> = chatFlow.filterNotNull()
        .map { it.members.sorted() }
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyMap<String, User>())
            else combine(ids.map { users.observeUser(it) }) { arr -> arr.filterNotNull().associateBy { it.uid } }
        }

    private val header: Flow<ChatHeaderUi> = chatFlow.filterNotNull().flatMapLatest { chat ->
        val typingFlow = realtime.observeTyping(chatId)
        if (chat.isGroup) {
            combine(typingFlow, memberUsers) { typing, members ->
                val names = typing.mapNotNull { members[it]?.name?.substringBefore(' ') }
                ChatHeaderUi(
                    title = chat.name ?: "Group",
                    photoUrl = chat.photoUrl,
                    isGroup = true,
                    subtitle = if (names.isNotEmpty()) typingLabel(names) else "${chat.members.size} members",
                    subtitleActive = names.isNotEmpty(),
                )
            }
        } else {
            val other = chat.otherMember(me) ?: me
            combine(users.observeUser(other), realtime.observePresence(other), typingFlow, now) { u, p: Presence, typing, t ->
                ChatHeaderUi(
                    title = u?.name ?: "Nook user",
                    photoUrl = u?.photoUrl,
                    isGroup = false,
                    subtitle = when {
                        typing.isNotEmpty() -> "typing…"
                        p.online -> "Online"
                        else -> TimeFormat.lastSeen(p.lastSeen, t)
                    },
                    subtitleActive = typing.isNotEmpty() || p.online,
                    otherUid = other,
                    online = p.online,
                )
            }
        }
    }

    private val typingNames: Flow<List<String>> = combine(realtime.observeTyping(chatId), memberUsers) { t, m ->
        t.mapNotNull { m[it]?.name?.substringBefore(' ') }
    }

    val state: StateFlow<ChatUiState> = combine(
        combine(chatFlow, live, older, retained, outbox.pending(chatId)) { c, l, o, r, p -> Sources(c, l, o, r, p) },
        combine(memberUsers, settings, newIds, now) { m, s, n, t -> Extras(m, s, n, t) },
        header,
        typingNames,
        combine(loadingMore, canLoadMore) { a, b -> a to b },
    ) { src, ex, h, typing, (lm, more) -> build(src, ex, h, typing, lm, more) }
        .catch { e -> emit(ChatUiState(loading = false, error = e.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    private data class Sources(val chat: Chat?, val live: LiveWindow?, val older: List<Message>, val retained: Map<String, Message>, val pending: List<Message>)
    private data class Extras(val members: Map<String, User>, val settings: PrivateSettings, val newIds: Set<String>, val now: Long)

    init {
        // Opening the chat sweeps a small batch of expired messages (no Cloud Functions on Spark).
        viewModelScope.launch {
            chatFlow.filterNotNull().first()
            runCatching { messages.deleteExpired(chatId) }
        }
    }

    private fun onLiveSnapshot(w: LiveWindow) {
        val current = w.messages.associateBy { it.id }
        if (firstSnapshot) {
            seenIds += current.keys
            firstSnapshot = false
            if (w.messages.size < AppConfig.PAGE_SIZE && !w.fromCache) canLoadMore.value = false
        } else {
            val fresh = current.keys - seenIds
            if (fresh.isNotEmpty()) newIds.update { it + fresh }
            seenIds += fresh
            // Messages that fell off the end of the limit window are kept (they were not deleted).
            val minLive = w.messages.minOfOrNull { it.createdAt } ?: Long.MAX_VALUE
            val dropped = (prevLive.keys - current.keys).mapNotNull { prevLive[it] }
                .filter { w.messages.size >= AppConfig.PAGE_SIZE && it.createdAt < minLive }
            if (dropped.isNotEmpty()) retained.update { it + dropped.associateBy { m -> m.id } }
        }
        prevLive = current
    }

    private fun build(src: Sources, ex: Extras, h: ChatHeaderUi, typing: List<String>, loadingMoreNow: Boolean, more: Boolean): ChatUiState {
        val chat = src.chat
        if (src.live == null && chat == null) return ChatUiState(loading = true, header = h)
        val liveMsgs = src.live?.messages.orEmpty()
        val byId = LinkedHashMap<String, Message>()
        src.older.forEach { byId[it.id] = it }
        src.retained.values.forEach { byId[it.id] = it }
        liveMsgs.forEach { byId[it.id] = it }
        src.pending.forEach { if (!byId.containsKey(it.id) || it.status == SendStatus.FAILED) byId[it.id] = it }

        val blocked = ex.settings.blocked.toSet()
        val list = byId.values
            .filter { m -> me !in m.hiddenFor && !m.isExpired(ex.now) && (m.senderId == me || m.senderId !in blocked) }
            .sortedByDescending { it.createdAt }

        val others = chat?.members?.filter { it != me }.orEmpty()
        val lastRead = chat?.lastRead.orEmpty()
        val myNewestId = list.firstOrNull { it.senderId == me && it.type != MessageType.SYSTEM }?.id

        val items = ArrayList<ChatItem>(list.size + 8)
        list.forEachIndexed { i, m ->
            val newer = list.getOrNull(i - 1)
            val olderMsg = list.getOrNull(i + 1)
            val mine = m.senderId == me
            val sameRun = { o: Message? ->
                o != null && o.senderId == m.senderId && o.type != MessageType.SYSTEM && m.type != MessageType.SYSTEM &&
                    kotlin.math.abs(o.createdAt - m.createdAt) < 5 * 60_000 && TimeFormat.isSameDay(o.createdAt, m.createdAt)
            }
            val seenBy = if (mine) others.count { (lastRead[it] ?: 0L) >= m.createdAt } else 0
            val sender = ex.members[m.senderId]
            val reactions = m.reactions.values.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
            items += ChatItem.Msg(
                MessageUi(
                    message = m,
                    mine = mine,
                    senderName = sender?.name,
                    senderPhoto = sender?.photoUrl,
                    showSender = chat?.isGroup == true && !mine && !sameRun(olderMsg),
                    joinPrev = sameRun(olderMsg),
                    joinNext = sameRun(newer),
                    isNew = m.id in ex.newIds || (m.status != SendStatus.SENT && m.id !in seenIds),
                    seen = seenBy > 0,
                    seenLabel = if (m.id == myNewestId && m.status == SendStatus.SENT && seenBy > 0) {
                        if (chat?.isGroup == true) (if (seenBy == others.size) "Seen by everyone" else "Seen by $seenBy") else "Seen"
                    } else null,
                    replySenderName = m.replyTo?.let { r -> if (r.senderId == me) "You" else ex.members[r.senderId]?.name },
                    reactionSummary = reactions,
                    myReaction = m.reactions[me],
                ),
            )
            if (olderMsg == null || !TimeFormat.isSameDay(olderMsg.createdAt, m.createdAt)) {
                items += ChatItem.DateSeparator(TimeFormat.separator(m.createdAt, ex.now), "sep_" + m.id)
            }
        }
        val otherUid = if (chat?.isGroup == false) chat.otherMember(me) else null
        return ChatUiState(
            loading = src.live == null,
            chat = chat,
            header = h,
            items = items,
            typing = typing,
            canLoadMore = more,
            loadingMore = loadingMoreNow,
            blockedOther = otherUid != null && otherUid in blocked,
            notMember = chat != null && me !in chat.members,
        )
    }

    fun markAnimated(id: String) = newIds.update { it - id }

    fun loadOlder() {
        if (loadingMore.value || !canLoadMore.value) return
        val oldest = state.value.items.lastOrNull { it is ChatItem.Msg }?.let { (it as ChatItem.Msg).ui.message } ?: return
        loadingMore.value = true
        viewModelScope.launch {
            val page = runCatching { messages.loadOlder(chatId, oldest.createdAt) }.getOrDefault(emptyList())
            seenIds += page.map { it.id }
            older.update { (it + page).distinctBy { m -> m.id } }
            if (page.size < AppConfig.PAGE_SIZE) canLoadMore.value = false
            loadingMore.value = false
        }
    }

    // ---- read receipts & typing ----

    private var lastMarked = 0L
    fun markReadIfNeeded() {
        val chat = chatFlow.value ?: return
        val lm = chat.lastMessage ?: return
        if (lm.senderId == me) return
        if (chat.lastMessageAt <= (chat.lastRead[me] ?: 0L) || chat.lastMessageAt <= lastMarked) return
        lastMarked = chat.lastMessageAt
        viewModelScope.launch { chats.markRead(chatId) }
    }

    private var lastTypingWrite = 0L
    private var typingStopJob: Job? = null
    fun onTextChanged(text: String) {
        if (text.isBlank()) { stopTyping(); return }
        val t = System.currentTimeMillis()
        if (t - lastTypingWrite > 3_000) { realtime.setTyping(chatId, true); lastTypingWrite = t }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch { delay(4_000); stopTyping() }
    }

    private fun stopTyping() {
        typingStopJob?.cancel()
        if (lastTypingWrite != 0L) { realtime.setTyping(chatId, false); lastTypingWrite = 0L }
    }

    // ---- sending ----

    fun setReply(m: Message?) { _replyTo.value = m }

    private fun replyRef(): ReplyRef? = _replyTo.value?.let { ReplyRef(it.id, it.senderId, it.type, it.previewText().take(120)) }

    private fun requireChat(): Chat? = chatFlow.value

    fun sendText(text: String) {
        val chat = requireChat() ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        outbox.send(chat, Payload.Ready(OutgoingMessage(messages.newId(chatId), MessageType.TEXT, body.take(4000), replyTo = replyRef())))
        _replyTo.value = null
        stopTyping()
    }

    fun sendImages(uris: List<Uri>, caption: String = "") {
        val chat = requireChat() ?: return
        val reply = replyRef()
        uris.forEachIndexed { i, u -> outbox.send(chat, Payload.Image(u, if (i == 0) caption else "", if (i == 0) reply else null)) }
        _replyTo.value = null
    }

    fun sendFile(uri: Uri) {
        val chat = requireChat() ?: return
        outbox.send(chat, Payload.Document(uri, replyRef()))
        _replyTo.value = null
    }

    fun sendVoice(file: File, durationMs: Long, waveform: List<Float>) {
        val chat = requireChat() ?: return
        outbox.send(chat, Payload.Voice(file, durationMs, waveform, replyRef()))
        _replyTo.value = null
    }

    fun sendGif(item: GiphyItem, sticker: Boolean) {
        val chat = requireChat() ?: return
        val type = if (sticker) MessageType.STICKER else MessageType.GIF
        outbox.send(chat, Payload.Ready(OutgoingMessage(messages.newId(chatId), type, media = Media(url = item.url, width = item.width, height = item.height, mime = "image/webp"), replyTo = replyRef())))
        _replyTo.value = null
    }

    fun sendSticker(sticker: Sticker) {
        val chat = requireChat() ?: return
        outbox.send(chat, Payload.Ready(OutgoingMessage(messages.newId(chatId), MessageType.STICKER, media = Media(url = sticker.url, width = 512, height = 512), replyTo = replyRef())))
        _replyTo.value = null
    }

    fun retry(id: String) = outbox.retry(id)
    fun discard(id: String) = outbox.discard(id)

    // ---- message actions ----

    fun react(m: Message, emoji: String) {
        val next = if (m.reactions[me] == emoji) null else emoji
        viewModelScope.launch { runCatching { messages.react(chatId, m.id, next) } }
    }

    fun unsend(m: Message) {
        val chat = requireChat() ?: return
        if (m.senderId != me) return
        viewModelScope.launch { runCatching { messages.unsend(chat, m) } }
    }

    fun deleteForMe(m: Message) {
        if (m.status != SendStatus.SENT) { outbox.discard(m.id); return }
        viewModelScope.launch { runCatching { messages.hideForMe(chatId, m.id) } }
    }

    fun forward(m: Message, chatIds: List<String>) {
        viewModelScope.launch {
            val all = chats.chatList.filterNotNull().first().associateBy { it.id }
            chatIds.mapNotNull { all[it] }.forEach { chat ->
                outbox.send(
                    chat,
                    Payload.Ready(OutgoingMessage(messages.newId(chat.id), m.type, m.text, m.media, forwarded = true)),
                )
            }
        }
    }

    fun seenBy(m: Message): List<User> {
        val chat = state.value.chat ?: return emptyList()
        return chat.members.filter { it != me && (chat.lastRead[it] ?: 0L) >= m.createdAt }
            .mapNotNull { uid -> cachedMembers[uid] }
    }

    private var cachedMembers: Map<String, User> = emptyMap()

    init {
        viewModelScope.launch { memberUsers.collect { cachedMembers = it } }
    }

    fun unblock() {
        val other = state.value.header.otherUid ?: return
        viewModelScope.launch { runCatching { users.unblock(other) } }
    }

    override fun onCleared() {
        stopTyping()
        super.onCleared()
    }

    companion object {
        fun typingLabel(names: List<String>): String = when (names.size) {
            1 -> "${names[0]} is typing…"
            2 -> "${names[0]} and ${names[1]} are typing…"
            else -> "${names.size} people are typing…"
        }
    }
}
