package com.nook.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.nook.app.MainActivity
import com.nook.app.R
import com.nook.app.feature.calls.IncomingCallActivity

/**
 * Builds every notification. Payloads never carry message content, so neither do these:
 * "Ali sent you a photo", "New message in The Boys".
 */
object Notifier {
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_CALLS = "calls"
    const val CHANNEL_ONGOING = "ongoing_call"

    const val EXTRA_CHAT_ID = "nook.chat_id"
    const val EXTRA_ANSWER_CALL_ID = "nook.answer_call_id"
    const val EXTRA_CALL_ID = "nook.call_id"
    const val EXTRA_CALLER_NAME = "nook.caller_name"
    const val EXTRA_CALL_VIDEO = "nook.call_video"

    private const val CALL_NOTIFICATION_ID = 4242
    const val ONGOING_NOTIFICATION_ID = 4343

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val messages = NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New messages from your friends"
        }
        val calls = NotificationChannel(CHANNEL_CALLS, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Full-screen incoming calls"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
            )
            enableVibration(true)
        }
        val ongoing = NotificationChannel(CHANNEL_ONGOING, "Ongoing call", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannels(listOf(messages, calls, ongoing))
    }

    private fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun immutable(flags: Int = PendingIntent.FLAG_UPDATE_CURRENT) = flags or PendingIntent.FLAG_IMMUTABLE

    fun showMessage(context: Context, chatId: String, title: String, body: String) {
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CHAT_ID, chatId)
        }
        val pi = PendingIntent.getActivity(context, chatId.hashCode(), open, immutable())
        val n = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF6A33.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pi)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(chatId, 1, n)
    }

    fun clearChat(context: Context, chatId: String) {
        NotificationManagerCompat.from(context).cancel(chatId, 1)
    }

    fun showIncomingCall(context: Context, callId: String, callerName: String, video: Boolean) {
        if (!canPost(context)) return
        val full = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALL_VIDEO, video)
        }
        val fullPi = PendingIntent.getActivity(context, callId.hashCode(), full, immutable())
        val answer = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ANSWER_CALL_ID, callId)
        }
        val answerPi = PendingIntent.getActivity(context, callId.hashCode() + 1, answer, immutable())
        val decline = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE
            putExtra(EXTRA_CALL_ID, callId)
        }
        val declinePi = PendingIntent.getBroadcast(context, callId.hashCode() + 2, decline, immutable())
        val caller = Person.Builder().setName(callerName).setImportant(true).build()
        val n = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFFF6A33.toInt())
            .setContentTitle(callerName)
            .setContentText(if (video) "Incoming video call" else "Incoming voice call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(45_000)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declinePi, answerPi))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(callId, CALL_NOTIFICATION_ID, n)
    }

    fun cancelIncomingCall(context: Context, callId: String) {
        NotificationManagerCompat.from(context).cancel(callId, CALL_NOTIFICATION_ID)
    }

    fun ongoingCall(context: Context, title: String): android.app.Notification {
        val open = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pi = PendingIntent.getActivity(context, 7, open, immutable())
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Call in progress")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pi)
            .build()
    }
}
