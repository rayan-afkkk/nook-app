package com.nook.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.data.model.Chat
import com.nook.app.data.model.Disappearing
import com.nook.app.data.model.Presence
import com.nook.app.data.model.User
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.ChatRepository
import com.nook.app.data.repo.RealtimeRepository
import com.nook.app.data.repo.StickerRepository
import com.nook.app.data.repo.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemberUi(val user: User, val presence: Presence, val isAdmin: Boolean, val isMe: Boolean)

data class ChatInfoUi(
    val chat: Chat? = null,
    val members: List<MemberUi> = emptyList(),
    val muted: Boolean = false,
    val blocked: Boolean = false,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatInfoViewModel(
    val chatId: String,
    private val chats: ChatRepository,
    private val users: UserRepository,
    private val realtime: RealtimeRepository,
    private val stickers: StickerRepository,
    auth: AuthRepository,
) : ViewModel() {
    val me = auth.uid.orEmpty()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val chatFlow = chats.observeChat(chatId)

    private val members = chatFlow.filterNotNull().flatMapLatest { chat ->
        if (chat.members.isEmpty()) flowOf(emptyList())
        else combine(chat.members.map { uid ->
            combine(users.observeUser(uid), realtime.observePresence(uid)) { u, p ->
                u?.let { MemberUi(it, p, uid in chat.admins, uid == me) }
            }
        }) { arr -> arr.filterNotNull().sortedWith(compareByDescending<MemberUi> { it.isMe }.thenByDescending { it.presence.online }.thenBy { it.user.name }) }
    }

    val state: StateFlow<ChatInfoUi> = combine(chatFlow, members, users.observePrivateSettings()) { chat, m, settings ->
        val other = chat?.takeIf { !it.isGroup }?.otherMember(me)
        ChatInfoUi(chat, m, chatId in settings.mutedChats, other != null && other in settings.blocked, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatInfoUi())

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try { block() } catch (e: Exception) { _error.value = e.message ?: "Something went wrong" }
            _busy.value = false
        }
    }

    fun clearError() { _error.value = null }
    fun setMuted(muted: Boolean) = work { users.setMuted(chatId, muted) }
    fun setDisappearing(d: Disappearing) = work { chats.setDisappearing(chatId, d) }
    fun rename(name: String) = work { if (name.isNotBlank()) chats.renameGroup(chatId, name) }
    fun leave(onDone: () -> Unit) = work { chats.leaveGroup(chatId); onDone() }
    fun setBlocked(blocked: Boolean) = work {
        val other = state.value.chat?.otherMember(me) ?: return@work
        if (blocked) users.block(other) else users.unblock(other)
    }
    fun createPack(name: String, onCreated: (String) -> Unit) = work {
        val chat = state.value.chat ?: return@work
        onCreated(stickers.create(chat, name.ifBlank { "${chat.name ?: "Our"} stickers" }))
    }
    fun removePhoto() = work { chats.setGroupPhoto(chatId, null) }

    val packs = stickers.myPacks().map { list -> list.filter { it.chatId == chatId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
