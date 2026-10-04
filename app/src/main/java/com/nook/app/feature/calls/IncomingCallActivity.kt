package com.nook.app.feature.calls

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nook.app.MainActivity
import com.nook.app.NookApplication
import com.nook.app.data.model.CallStatus
import com.nook.app.data.repo.CallRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.designsystem.theme.InstrumentSerif
import com.nook.app.designsystem.theme.NookPalette
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.notifications.Notifier
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.koin.android.ext.android.inject

/** Full-screen incoming call, shown over the lock screen via the notification's full-screen intent. */
class IncomingCallActivity : ComponentActivity() {
    private val calls: CallRepository by inject()
    private val users: UserRepository by inject()
    private val manager: CallManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val callId = intent.getStringExtra(Notifier.EXTRA_CALL_ID)
        if (callId == null || !(application as NookApplication).firebaseReady) { finish(); return }
        val name = intent.getStringExtra(Notifier.EXTRA_CALLER_NAME) ?: "Someone"
        val video = intent.getBooleanExtra(Notifier.EXTRA_CALL_VIDEO, false)

        setContent {
            NookTheme {
                val call by remember(callId) { calls.observe(callId) }.collectAsState(initial = null)
                val photo by remember(call?.callerId) {
                    call?.callerId?.let { uid -> users.observeUser(uid).filterNotNull().map { it.photoUrl } } ?: emptyFlow()
                }.collectAsState(initial = null)
                LaunchedEffect(call?.status) {
                    val st = call?.status
                    if (st != null && st != CallStatus.RINGING) {
                        Notifier.cancelIncomingCall(this@IncomingCallActivity, callId)
                        finish()
                    }
                }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(48.dp))
                        RingingAvatar(photo, name, pulsing = true)
                        Spacer(Modifier.height(28.dp))
                        Text(name, fontFamily = InstrumentSerif, fontSize = 44.sp, color = NookPalette.Cream)
                        Text(if (video) "Incoming video call" else "Incoming voice call", style = NookTheme.type.body, color = NookPalette.Muted)
                        Spacer(Modifier.weight(1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            RoundAction(Icons.Rounded.CallEnd, "Decline", NookPalette.Coral) {
                                manager.decline(callId)
                                finish()
                            }
                            RoundAction(Icons.Rounded.Call, "Accept", Color(0xFF6BD69A)) {
                                Notifier.cancelIncomingCall(this@IncomingCallActivity, callId)
                                startActivity(
                                    Intent(this@IncomingCallActivity, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        putExtra(Notifier.EXTRA_ANSWER_CALL_ID, callId)
                                    },
                                )
                                finish()
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(color).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.Black, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, style = NookTheme.type.label, color = NookPalette.Cream)
    }
}
