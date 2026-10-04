package com.nook.app.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nook.app.data.repo.UserRepository
import com.nook.app.session.ActiveChat
import com.nook.app.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Receives the Worker's data-only FCM messages. Payload keys (no message content, ever):
 *   type=message  chatId, title, body
 *   type=call     callId, callerName, callType
 *   type=call_cancel callId
 */
class NookMessagingService : FirebaseMessagingService() {

    private val users: UserRepository by inject()
    private val session: SessionManager by inject()
    private val appScope: CoroutineScope by inject()

    override fun onNewToken(token: String) {
        appScope.launch { runCatching { users.saveFcmToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        when (d["type"]) {
            "message" -> {
                val chatId = d["chatId"] ?: return
                val viewing = session.foreground.value && ActiveChat.chatId == chatId
                if (!viewing) Notifier.showMessage(this, chatId, d["title"] ?: "Nook", d["body"] ?: "New message")
            }
            "call" -> {
                val callId = d["callId"] ?: return
                Notifier.showIncomingCall(this, callId, d["callerName"] ?: "Someone", d["callType"] == "video")
            }
            "call_cancel" -> d["callId"]?.let { Notifier.cancelIncomingCall(this, it) }
        }
    }
}
