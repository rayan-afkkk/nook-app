package com.nook.app.feature.chats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.data.model.Chat
import com.nook.app.data.model.MessageType
import com.nook.app.data.model.Presence
import com.nook.app.data.model.User
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.ChatRepository
import com.nook.app.data.repo.ConnectivityRepository
import com.nook.app.data.repo.RealtimeRepository
import com.nook.app.data.repo.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ChatFilter(val label: String) { All("All"), Direct("Direct"), Groups("Groups"), Unread("Unread") }

data class ChatRowUi(
    val chatId: String,
    val title: String,
    val photoUrl: String?,
    val isGroup: Boolean,
    val online: Boolean,
    val preview: String,
    val previewType: MessageType?,
    val previewIsMine: Boolean,
    val time: Long,
    val unread: Boolean,
)

sealed interface ChatsUiState {
    data object Loading : ChatsUiState
    data class Ready(val rows: List<ChatRowUi>, val totalChats: Int) : ChatsUiState
    data class Error(val message: String) : ChatsUiState
}

class ChatsViewModel(
    private val handle: SavedStateHandle,
    chats: ChatRepository,
    private val users: UserRepository,
    private val realtime: RealtimeRepository,
    private val auth: AuthRepository,
    connectivity: ConnectivityRepository,
) : ViewModel() {

    val filter: StateFlow<ChatFilter> = handle.getStateFlow("filter", ChatFilter.All)
    val query: StateFlow<String> = handle.getStateFlow("query", "")
    val searching: StateFlow<Boolean> = handle.getStateFlow("searching", false)
    val online: StateFlow<Boolean> = connectivity.online

    fun setFilter(f: ChatFilter) { handle["filter"] = f }
    fun setQuery(q: String) { handle["query"] = q }
    fun setSearching(on: Boolean) { handle["searching"] = on; if (!on) handle["query"] = "" }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows: Flow<List<ChatRowUi>?> = combine(chats.chatList, users.observePrivateSettings()) { list, settings -> list to settings }
        .flatMapLatest { (list, settings) ->
            if (list == null) return@flatMapLatest flowOf(null)
            val me = auth.uid ?: return@flatMapLatest flowOf(emptyList())
            val visible = list.filter { c -> c.isGroup || c.otherMember(me) !in settings.blocked }
            if (visible.isEmpty()) flowOf(emptyList())
            else combine(visible.map { rowFlow(it, me) }) { it.toList() }
        }

    val state: StateFlow<ChatsUiState> = combine(rows, filter, query) { r, f, q ->
        if (r == null) return@combine ChatsUiState.Loading
        val filtered = r.filter { row ->
            when (f) {
                ChatFilter.All -> true
                ChatFilter.Direct -> !row.isGroup
                ChatFilter.Groups -> row.isGroup
                ChatFilter.Unread -> row.unread
            } && (q.isBlank() || row.title.contains(q.trim(), ignoreCase = true))
        }
        ChatsUiState.Ready(filtered, r.size) as ChatsUiState
    }
        .catch { emit(ChatsUiState.Error(it.message ?: "Couldn't load chats")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsUiState.Loading)

    private fun rowFlow(chat: Chat, me: String): Flow<ChatRowUi> {
        val lm = chat.lastMessage
        val senderFlow: Flow<User?> = if (chat.isGroup && lm != null && lm.senderId != me) users.observeUser(lm.senderId) else flowOf(null)
        return if (chat.isGroup) {
            senderFlow.map { sender -> build(chat, me, chat.name ?: "Group", chat.photoUrl, Presence(), sender) }
        } else {
            val other = chat.otherMember(me) ?: me
            combine(users.observeUser(other), realtime.observePresence(other)) { u, p ->
                build(chat, me, u?.name ?: "Nook user", u?.photoUrl, p, null)
            }
        }
    }

    private fun build(chat: Chat, me: String, title: String, photo: String?, presence: Presence, sender: User?): ChatRowUi {
        val lm = chat.lastMessage
        val base = lm?.preview?.takeIf { it.isNotBlank() } ?: if (lm == null) "Say hi 👋" else ""
        val preview = when {
            lm == null -> base
            lm.type == MessageType.SYSTEM -> base
            lm.senderId == me -> "You: $base"
            chat.isGroup && sender != null -> "${sender.name.substringBefore(' ')}: $base"
            else -> base
        }
        return ChatRowUi(
            chatId = chat.id,
            title = title,
            photoUrl = photo,
            isGroup = chat.isGroup,
            online = presence.online,
            preview = preview,
            previewType = lm?.type,
            previewIsMine = lm?.senderId == me,
            time = chat.lastMessageAt,
            unread = chat.isUnread(me),
        )
    }
}
