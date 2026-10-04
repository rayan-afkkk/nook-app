package com.nook.app.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DeepLink {
    data class OpenChat(val chatId: String) : DeepLink
    data class AnswerCall(val callId: String) : DeepLink
}

/** Notifications → MainActivity → here → NavHost (once the user is past the gates). */
class DeepLinkBus {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()
    fun post(link: DeepLink) { _pending.value = link }
    fun consume() { _pending.value = null }
}

/** Which chat is on screen, so we don't notify for the conversation you're looking at. */
object ActiveChat {
    @Volatile var chatId: String? = null
}
