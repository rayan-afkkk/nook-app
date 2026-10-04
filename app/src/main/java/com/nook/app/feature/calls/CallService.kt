package com.nook.app.feature.calls

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.nook.app.notifications.Notifier

/** Keeps the process alive (mic/camera) while a call is in progress. */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 0
        runCatching {
            ServiceCompat.startForeground(this, Notifier.ONGOING_NOTIFICATION_ID, Notifier.ongoingCall(this, "Nook call"), type)
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }
}
