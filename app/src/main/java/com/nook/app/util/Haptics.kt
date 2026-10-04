package com.nook.app.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Thin wrapper over View haptics so every call site uses the same vocabulary. */
@Stable
class Haptics(private val view: View) {
    /** Light tick: tab/page change, toggles, segmented controls. */
    fun tick() { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }

    /** Taps on primary actions. */
    fun tap() { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }

    /** Long-press menus. */
    fun longPress() { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }

    /** Message sent / reaction landed. */
    fun confirm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun reject() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
