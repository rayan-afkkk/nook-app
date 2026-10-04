package com.nook.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nook.app.data.model.CallStatus
import com.nook.app.data.repo.CallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** "Decline" from the incoming-call notification. */
class CallActionReceiver : BroadcastReceiver(), KoinComponent {
    private val calls: CallRepository by inject()
    private val appScope: CoroutineScope by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(Notifier.EXTRA_CALL_ID) ?: return
        Notifier.cancelIncomingCall(context, callId)
        if (intent.action == ACTION_DECLINE) {
            val pending = goAsync()
            appScope.launch {
                runCatching { calls.setStatus(callId, CallStatus.DECLINED) }
                pending.finish()
            }
        }
    }

    companion object { const val ACTION_DECLINE = "com.nook.app.action.DECLINE_CALL" }
}
