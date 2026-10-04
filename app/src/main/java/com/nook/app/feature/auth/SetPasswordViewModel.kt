package com.nook.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.data.security.AppLockManager
import com.nook.app.data.security.PasswordHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PasswordStep { VerifyCurrent, Enter, Confirm }

data class SetPasswordUiState(
    val step: PasswordStep = PasswordStep.Enter,
    val input: String = "",
    val error: String? = null,
    val working: Boolean = false,
    val useBiometric: Boolean = false,
)

/** Not persisted in SavedStateHandle on purpose: passwords must never be written to disk. */
class SetPasswordViewModel(
    private val change: Boolean,
    private val lock: AppLockManager,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SetPasswordUiState(step = if (change) PasswordStep.VerifyCurrent else PasswordStep.Enter, useBiometric = lock.biometricEnabled.value),
    )
    val state: StateFlow<SetPasswordUiState> = _state.asStateFlow()
    private var first: String = ""

    fun onInput(v: String) = _state.update { it.copy(input = v.take(64), error = null) }
    fun setBiometric(on: Boolean) = _state.update { it.copy(useBiometric = on) }

    fun next(onDone: () -> Unit) {
        val s = _state.value
        if (s.working) return
        when (s.step) {
            PasswordStep.VerifyCurrent -> viewModelScope.launch {
                _state.update { it.copy(working = true) }
                val ok = lock.verify(s.input)
                _state.update {
                    if (ok) it.copy(step = PasswordStep.Enter, input = "", working = false)
                    else it.copy(error = "That's not your current password", working = false)
                }
            }
            PasswordStep.Enter -> {
                val err = PasswordHasher.validate(s.input)
                if (err != null) _state.update { it.copy(error = err) }
                else { first = s.input; _state.update { it.copy(step = PasswordStep.Confirm, input = "") } }
            }
            PasswordStep.Confirm -> {
                val err = PasswordHasher.validate(first, s.input)
                if (err != null) {
                    _state.update { it.copy(error = err, input = "") }
                    return
                }
                viewModelScope.launch {
                    _state.update { it.copy(working = true) }
                    lock.setPassword(first)
                    lock.setBiometric(s.useBiometric)
                    first = ""
                    onDone()
                }
            }
        }
    }

    fun back(): Boolean {
        if (_state.value.step == PasswordStep.Confirm) {
            _state.update { it.copy(step = PasswordStep.Enter, input = "", error = null) }
            return true
        }
        return false
    }
}
