package com.nook.app.feature.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nook.app.AppConfig
import com.nook.app.data.model.Disappearing
import com.nook.app.data.model.PrivateSettings
import com.nook.app.data.model.User
import com.nook.app.data.prefs.SettingsStore
import com.nook.app.data.remote.MediaAsset
import com.nook.app.data.remote.WorkerApi
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.CacheRepository
import com.nook.app.data.repo.ChatRepository
import com.nook.app.data.repo.MessageRepository
import com.nook.app.data.repo.StorageInfo
import com.nook.app.data.repo.UserRepository
import com.nook.app.designsystem.theme.ThemeMode
import com.nook.app.session.SignOutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val users: UserRepository,
    private val chats: ChatRepository,
    private val messages: MessageRepository,
    private val settings: SettingsStore,
    private val cache: CacheRepository,
    private val worker: WorkerApi,
    private val auth: AuthRepository,
    private val signOutUseCase: SignOutUseCase,
    people: com.nook.app.data.repo.PeopleRepository,
) : ViewModel() {
    /** Little numbers for the profile header: chats, friends. */
    val stats: StateFlow<Pair<Int, Int>> = kotlinx.coroutines.flow.combine(
        chats.chatList.filterNotNull(),
        people.contacts,
    ) { list, contacts -> list.size to contacts.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)

    val me: StateFlow<User?> = users.observeMe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val privateSettings: StateFlow<PrivateSettings> = users.observePrivateSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrivateSettings())
    val theme: StateFlow<ThemeMode> = settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.Dark)
    private val _storage = MutableStateFlow<StorageInfo?>(null)
    val storage: StateFlow<StorageInfo?> = _storage.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { refreshStorage() }

    fun refreshStorage() { viewModelScope.launch { _storage.value = runCatching { cache.info() }.getOrNull() } }

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { settings.setTheme(mode) } }

    fun clearCache() {
        viewModelScope.launch {
            runCatching { cache.clearMediaCache() }
            refreshStorage()
            _message.value = "Cache cleared"
        }
    }

    fun setDisappearingDefault(d: Disappearing) { viewModelScope.launch { runCatching { users.setDisappearingDefault(d) } } }

    fun removePhoto() {
        viewModelScope.launch {
            val old = runCatching { users.currentPhotoPublicId() }.getOrNull()
            runCatching { users.updatePhoto(null, null) }
            if (old != null && AppConfig.hasWorker) runCatching { worker.deleteMedia("avatar", listOf(MediaAsset(old, "image"))) }
        }
    }

    fun signOut(context: Context, onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { signOutUseCase(context) }
            _busy.value = false
            onDone()
        }
    }

    /** Deletes my messages (+media), leaves groups, removes profile/username/tokens, then the auth user. */
    fun deleteAccount(activityContext: Context, onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                messages.deleteAllMine()
                val myChats = chats.chatList.filterNotNull().first()
                myChats.filter { it.isGroup }.forEach { runCatching { chats.leaveGroup(it.id) } }
                users.currentPhotoPublicId()?.let { id ->
                    if (AppConfig.hasWorker) runCatching { worker.deleteMedia("avatar", listOf(MediaAsset(id, "image"))) }
                }
                users.deleteAccountData()
                runCatching { auth.deleteAuthUser(activityContext) }
                runCatching { signOutUseCase(activityContext) }
                onDone()
            } catch (e: Exception) {
                _message.value = e.message ?: "Couldn't delete your account. Try again."
            }
            _busy.value = false
        }
    }

    fun clearMessage() { _message.value = null }
}
