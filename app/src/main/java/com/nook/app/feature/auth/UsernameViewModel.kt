package com.nook.app.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.data.repo.UsernameRules
import com.nook.app.data.repo.UsernameTakenException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Availability {
    data object Idle : Availability
    data object Checking : Availability
    data object Available : Availability
    data object Taken : Availability
    data class Invalid(val reason: String) : Availability
    data object Offline : Availability
}

data class UsernameUiState(
    val displayName: String = "",
    val username: String = "",
    val photoUrl: String? = null,
    val availability: Availability = Availability.Idle,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSubmit get() = availability == Availability.Available && displayName.isNotBlank() && !saving
}

class UsernameViewModel(
    private val handle: SavedStateHandle,
    private val auth: AuthRepository,
    private val users: UserRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        UsernameUiState(
            displayName = handle["displayName"] ?: auth.currentUser?.displayName.orEmpty(),
            username = handle["username"] ?: "",
            photoUrl = auth.currentUser?.photoUrl?.toString(),
        ),
    )
    val state: StateFlow<UsernameUiState> = _state.asStateFlow()
    private var checkJob: Job? = null

    init {
        if (_state.value.username.isNotEmpty()) onUsername(_state.value.username)
    }

    fun onDisplayName(v: String) {
        val trimmed = v.take(40)
        handle["displayName"] = trimmed
        _state.update { it.copy(displayName = trimmed) }
    }

    fun onUsername(raw: String) {
        val name = UsernameRules.normalize(raw).take(UsernameRules.MAX)
        handle["username"] = name
        _state.update { it.copy(username = name, error = null) }
        checkJob?.cancel()
        val invalid = if (name.isEmpty()) null else UsernameRules.validate(name)
        when {
            name.isEmpty() -> _state.update { it.copy(availability = Availability.Idle) }
            invalid != null -> _state.update { it.copy(availability = Availability.Invalid(invalid)) }
            else -> {
                _state.update { it.copy(availability = Availability.Checking) }
                checkJob = viewModelScope.launch {
                    delay(400)
                    val result = runCatching { users.isUsernameAvailable(name) }
                    _state.update {
                        if (it.username != name) it
                        else it.copy(
                            availability = result.fold(
                                onSuccess = { ok -> if (ok) Availability.Available else Availability.Taken },
                                onFailure = { Availability.Offline },
                            ),
                        )
                    }
                }
            }
        }
    }

    fun submit(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                users.createProfile(s.username, s.displayName, s.photoUrl)
                onDone()
            } catch (e: UsernameTakenException) {
                _state.update { it.copy(saving = false, availability = Availability.Taken) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "Couldn't save. Try again.") }
            }
        }
    }
}
