package com.nook.app.feature.lock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.data.repo.UserRepository
import com.nook.app.data.security.AppLockManager
import com.nook.app.session.SignOutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockUiState(val input: String = "", val error: Boolean = false, val checking: Boolean = false, val attempts: Int = 0)

class LockViewModel(
    val lock: AppLockManager,
    users: UserRepository,
    private val signOut: SignOutUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()
    val me = users.observeMe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onInput(v: String) = _state.update { it.copy(input = v.take(64), error = false) }

    fun submit(onWrong: () -> Unit) {
        val s = _state.value
        if (s.checking || s.input.isEmpty()) return
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            val ok = lock.unlock(s.input)
            if (ok) _state.value = LockUiState()
            else { _state.update { it.copy(checking = false, error = true, input = "", attempts = it.attempts + 1) }; onWrong() }
        }
    }

    fun biometricSuccess() { lock.unlockWithBiometric(); _state.value = LockUiState() }

    fun forgot(context: Context, onSignedOut: () -> Unit) {
        viewModelScope.launch { runCatching { signOut(context) }; onSignedOut() }
    }
}
