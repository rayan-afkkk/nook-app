package com.nook.app.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.SignInCancelled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(val loading: Boolean = false, val error: String? = null)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun signIn(activityContext: Context, onSignedIn: () -> Unit) {
        if (_state.value.loading) return
        _state.value = LoginUiState(loading = true)
        viewModelScope.launch {
            try {
                auth.signInWithGoogle(activityContext)
                _state.value = LoginUiState()
                onSignedIn()
            } catch (e: SignInCancelled) {
                _state.value = LoginUiState()
            } catch (e: Exception) {
                _state.update { LoginUiState(error = e.message ?: "Couldn't sign in. Check your connection and try again.") }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }
}
