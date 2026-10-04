package com.nook.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.nook.app.feature.root.NookRoot
import com.nook.app.feature.root.SetupNeededScreen
import com.nook.app.notifications.Notifier
import com.nook.app.session.DeepLink
import com.nook.app.session.DeepLinkBus
import org.koin.android.ext.android.inject

/** FragmentActivity (a ComponentActivity) so BiometricPrompt can attach. */
class MainActivity : FragmentActivity() {

    private val deepLinks: DeepLinkBus by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Blank the app in Recents and block screenshots of private chats.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val ready = (application as NookApplication).firebaseReady
        if (ready) handleIntent(intent)
        setContent {
            if (ready) NookRoot() else SetupNeededScreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if ((application as NookApplication).firebaseReady) handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notifier.EXTRA_CHAT_ID)?.let { deepLinks.post(DeepLink.OpenChat(it)) }
        intent.getStringExtra(Notifier.EXTRA_ANSWER_CALL_ID)?.let { deepLinks.post(DeepLink.AnswerCall(it)) }
        intent.removeExtra(Notifier.EXTRA_CHAT_ID)
        intent.removeExtra(Notifier.EXTRA_ANSWER_CALL_ID)
    }
}
