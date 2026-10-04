package com.nook.app.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.data.model.User
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.ChatRepository
import com.nook.app.data.repo.PeopleRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.data.repo.UsernameRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UserSearch {
    data object Idle : UserSearch
    data object Searching : UserSearch
    data class Found(val user: User) : UserSearch
    data object NotFound : UserSearch
    data object Self : UserSearch
    data class Failed(val message: String) : UserSearch
}

data class NewChatUiState(
    val query: String = "",
    val search: UserSearch = UserSearch.Idle,
    val selected: List<User> = emptyList(),
    val groupName: String = "",
    val working: Boolean = false,
    val error: String? = null,
    val existingMembers: Set<String> = emptySet(),
)

class NewChatViewModel(
    private val addToChatId: String?,
    private val users: UserRepository,
    private val chats: ChatRepository,
    private val auth: AuthRepository,
    people: PeopleRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NewChatUiState())
    val state: StateFlow<NewChatUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    val suggestions: StateFlow<List<User>> = combine(
        people.contacts,
        if (addToChatId != null) chats.observeChat(addToChatId).map { it?.members.orEmpty().toSet() } else flowOf(emptySet<String>()),
    ) { contacts, members -> contacts.map { it.user }.filter { it.uid !in members } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQuery(q: String) {
        val name = UsernameRules.normalize(q).take(UsernameRules.MAX)
        _state.update { it.copy(query = name, error = null) }
        searchJob?.cancel()
        if (name.length < UsernameRules.MIN) { _state.update { it.copy(search = UserSearch.Idle) }; return }
        _state.update { it.copy(search = UserSearch.Searching) }
        searchJob = viewModelScope.launch {
            delay(350)
            val result = runCatching { users.findByUsername(name) }
            _state.update { s ->
                if (s.query != name) s
                else s.copy(
                    search = result.fold(
                        onSuccess = { u -> when { u == null -> UserSearch.NotFound; u.uid == auth.uid -> UserSearch.Self; else -> UserSearch.Found(u) } },
                        onFailure = { UserSearch.Failed("Couldn't search right now") },
                    ),
                )
            }
        }
    }

    fun toggle(user: User) = _state.update { s ->
        val sel = if (s.selected.any { it.uid == user.uid }) s.selected.filterNot { it.uid == user.uid } else s.selected + user
        s.copy(selected = sel)
    }

    fun onGroupName(n: String) = _state.update { it.copy(groupName = n.take(40)) }

    fun openDirect(user: User, onOpened: (String) -> Unit) = launchWork { onOpened(chats.openDirect(user.uid)) }

    fun createGroup(onOpened: (String) -> Unit) {
        val s = _state.value
        if (s.selected.isEmpty()) { _state.update { it.copy(error = "Pick at least one friend") }; return }
        val name = s.groupName.ifBlank { (s.selected.map { it.name.substringBefore(' ') }).joinToString(", ").take(40) }
        launchWork { onOpened(chats.createGroup(name, s.selected.map { it.uid })) }
    }

    fun addToGroup(onDone: () -> Unit) {
        val chatId = addToChatId ?: return
        val s = _state.value
        if (s.selected.isEmpty()) return
        launchWork {
            chats.addMembers(chatId, s.selected.map { it.uid }, s.selected.map { it.name })
            onDone()
        }
    }

    private fun launchWork(block: suspend () -> Unit) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            try { block() } catch (e: Exception) { _state.update { it.copy(error = e.message ?: "Something went wrong") } }
            _state.update { it.copy(working = false) }
        }
    }

    suspend fun chatMembers(): Set<String> = addToChatId?.let { chats.observeChat(it).first()?.members?.toSet() }.orEmpty()
}
