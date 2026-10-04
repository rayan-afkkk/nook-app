package com.nook.app.feature.friends

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.data.model.CallType
import com.nook.app.data.model.Chat
import com.nook.app.data.model.User
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.ChatRepository
import com.nook.app.data.repo.ConnectivityRepository
import com.nook.app.data.repo.Contact
import com.nook.app.data.repo.PeopleRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.data.repo.UsernameRules
import com.nook.app.feature.calls.CallManager
import com.nook.app.feature.chats.UserSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FriendsViewModel(
    private val handle: SavedStateHandle,
    people: PeopleRepository,
    private val users: UserRepository,
    private val chats: ChatRepository,
    private val auth: AuthRepository,
    private val calls: CallManager,
    connectivity: ConnectivityRepository,
) : ViewModel() {
    val online = connectivity.online
    val query: StateFlow<String> = handle.getStateFlow("q", "")
    private val _search = MutableStateFlow<UserSearch>(UserSearch.Idle)
    val search: StateFlow<UserSearch> = _search.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var job: Job? = null

    /** null while loading. */
    val contacts: StateFlow<List<Contact>?> = combine(people.contacts, users.observePrivateSettings()) { list, s ->
        list.filter { it.user.uid !in s.blocked }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val groups: StateFlow<List<Chat>> = chats.chatList.map { it.orEmpty().filter { c -> c.isGroup } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { if (query.value.isNotBlank()) onQuery(query.value) }

    fun onQuery(raw: String) {
        val name = UsernameRules.normalize(raw).take(UsernameRules.MAX)
        handle["q"] = name
        job?.cancel()
        if (name.length < UsernameRules.MIN) { _search.value = UserSearch.Idle; return }
        _search.value = UserSearch.Searching
        job = viewModelScope.launch {
            delay(350)
            _search.value = runCatching { users.findByUsername(name) }.fold(
                onSuccess = { u -> when { u == null -> UserSearch.NotFound; u.uid == auth.uid -> UserSearch.Self; else -> UserSearch.Found(u) } },
                onFailure = { UserSearch.Failed("Couldn't search right now") },
            )
        }
    }

    fun message(user: User, onOpen: (String) -> Unit) = viewModelScope.launch {
        runCatching { chats.openDirect(user.uid) }.onSuccess(onOpen).onFailure { _message.value = it.message }
    }

    fun call(user: User, video: Boolean) = viewModelScope.launch {
        runCatching { chats.openDirect(user.uid) }
            .onSuccess { chatId -> calls.startCall(chatId, user.uid, if (video) CallType.VIDEO else CallType.VOICE) }
            .onFailure { _message.value = it.message }
    }

    fun addToGroup(user: User, group: Chat) = viewModelScope.launch {
        if (user.uid in group.members) { _message.value = "${user.name} is already in ${group.name}"; return@launch }
        runCatching { chats.addMembers(group.id, listOf(user.uid), listOf(user.name)) }
            .onSuccess { _message.value = "Added ${user.name} to ${group.name}" }
            .onFailure { _message.value = it.message }
    }

    fun block(user: User) = viewModelScope.launch {
        runCatching { users.block(user.uid) }.onSuccess { _message.value = "Blocked ${user.name}" }
    }

    fun clearMessage() { _message.value = null }
}
