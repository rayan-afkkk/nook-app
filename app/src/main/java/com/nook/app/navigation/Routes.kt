package com.nook.app.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes. Arguments survive process death via the back stack.

@Serializable data object SplashRoute
@Serializable data class OnboardingRoute(val replay: Boolean = false)
@Serializable data object LoginRoute
@Serializable data object UsernameRoute
@Serializable data object PermissionsRoute
@Serializable data class SetPasswordRoute(val change: Boolean = false)
@Serializable data object MainRoute

@Serializable data class ChatRoute(val chatId: String)
@Serializable data class ChatInfoRoute(val chatId: String)
@Serializable data class NewChatRoute(val group: Boolean = false, val addToChatId: String? = null)
@Serializable data class CropRoute(val uri: String, val target: String)
@Serializable data object EditProfileRoute
@Serializable data object AppLockSettingsRoute
@Serializable data object BlockedUsersRoute
@Serializable data object NotificationSettingsRoute
@Serializable data class LegalRoute(val kind: String)
@Serializable data class StickerPackRoute(val packId: String)

object CropTargets {
    const val AVATAR = "avatar"
    fun group(chatId: String) = "group:$chatId"
    fun pack(packId: String) = "pack:$packId"
}
