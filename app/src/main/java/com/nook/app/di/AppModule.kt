package com.nook.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.nook.app.data.media.VoicePlayer
import com.nook.app.data.prefs.SettingsStore
import com.nook.app.data.remote.CloudinaryApi
import com.nook.app.data.remote.GiphyApi
import com.nook.app.data.remote.WorkerApi
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.CacheRepository
import com.nook.app.data.repo.CallRepository
import com.nook.app.data.repo.ChatRepository
import com.nook.app.data.repo.ConnectivityRepository
import com.nook.app.data.repo.MediaRepository
import com.nook.app.data.repo.MessageRepository
import com.nook.app.data.repo.Outbox
import com.nook.app.data.repo.PeopleRepository
import com.nook.app.data.repo.RealtimeRepository
import com.nook.app.data.repo.StickerRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.data.security.AppLockManager
import com.nook.app.data.security.PasswordHasher
import com.nook.app.feature.account.AccountViewModel
import com.nook.app.feature.account.BlockedUsersViewModel
import com.nook.app.feature.account.CropViewModel
import com.nook.app.feature.auth.LoginViewModel
import com.nook.app.feature.auth.SetPasswordViewModel
import com.nook.app.feature.auth.UsernameViewModel
import com.nook.app.feature.calls.CallManager
import com.nook.app.feature.calls.CallsViewModel
import com.nook.app.feature.chat.ChatInfoViewModel
import com.nook.app.feature.chat.ChatViewModel
import com.nook.app.feature.chat.ExpressionsViewModel
import com.nook.app.feature.chats.ChatsViewModel
import com.nook.app.feature.chats.NewChatViewModel
import com.nook.app.feature.friends.FriendsViewModel
import com.nook.app.feature.lock.LockViewModel
import com.nook.app.feature.root.RootViewModel
import com.nook.app.feature.stickers.StickerPackViewModel
import com.nook.app.feature.stickers.StickersViewModel
import com.nook.app.session.DeepLinkBus
import com.nook.app.session.SessionManager
import com.nook.app.session.SignOutUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Everything is a lazy singleton: Firebase objects are only created when first used, so a build
 * without google-services.json can still start and show the setup screen.
 */
val appModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true } }
    single {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    // Firebase
    single { FirebaseAuth.getInstance() }
    single { FirebaseFirestore.getInstance() }
    single { FirebaseDatabase.getInstance() }

    // Local
    single { SettingsStore(androidContext()) }
    single { PasswordHasher() }
    single { AppLockManager(get<SettingsStore>(), get(), get()) }
    single { DeepLinkBus() }
    single { VoicePlayer(androidContext()) }
    single { CacheRepository(androidContext()) }
    single { ConnectivityRepository(androidContext(), get()) }

    // Remote
    single { CloudinaryApi(get(), get()) }
    single { GiphyApi(get(), get()) }
    single {
        val auth = get<AuthRepository>()
        WorkerApi(get(), get()) { auth.idToken() }
    }

    // Repositories
    single { AuthRepository(get()) }
    single { UserRepository(get(), get(), get()) }
    single { RealtimeRepository(get(), get()) }
    single { ChatRepository(get(), get(), get(), get(), get()) }
    single { MessageRepository(get(), get(), get(), get()) }
    single { MediaRepository(androidContext(), get()) }
    single { Outbox(get(), get(), get(), get()) }
    single { CallRepository(get(), get()) }
    single { StickerRepository(get(), get()) }
    single { PeopleRepository(get(), get(), get(), get()) }

    // Session
    single { SessionManager(get(), get(), get(), get(), get(), get()) }
    single { SignOutUseCase(get(), get(), get(), get(), get()) }
    single { CallManager(androidContext(), get(), get(), get(), get()) }

    // ViewModels
    viewModelOf(::RootViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::UsernameViewModel)
    viewModel { (change: Boolean) -> SetPasswordViewModel(change, get()) }
    viewModelOf(::LockViewModel)
    viewModelOf(::ChatsViewModel)
    viewModel { params -> NewChatViewModel(params.getOrNull(), get(), get(), get(), get()) }
    viewModel { (chatId: String) -> ChatViewModel(chatId, get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (chatId: String) -> ChatInfoViewModel(chatId, get(), get(), get(), get(), get()) }
    viewModelOf(::ExpressionsViewModel)
    viewModelOf(::FriendsViewModel)
    viewModelOf(::StickersViewModel)
    viewModel { (packId: String) -> StickerPackViewModel(packId, get(), get(), androidContext()) }
    viewModelOf(::CallsViewModel)
    viewModelOf(::AccountViewModel)
    viewModel { (uri: String, target: String) -> CropViewModel(uri, target, androidContext(), get(), get(), get(), get(), get()) }
    viewModelOf(::BlockedUsersViewModel)
}
