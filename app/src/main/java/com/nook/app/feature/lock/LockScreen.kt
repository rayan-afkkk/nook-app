package com.nook.app.feature.lock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.FieldHint
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookButtonStyle
import com.nook.app.designsystem.components.NookConfirmDialog
import com.nook.app.designsystem.components.NookIconButton
import com.nook.app.designsystem.components.NookTextField
import com.nook.app.designsystem.components.breathingGlow
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.feature.auth.AnimatedGradient
import com.nook.app.util.rememberHaptics
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** Full-screen lock overlay. Swallows all touches beneath it. */
@Composable
fun LockScreen(onSignedOut: () -> Unit, vm: LockViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val s by vm.state.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val biometricOn by vm.lock.biometricEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    var confirmForgot by remember { mutableStateOf(false) }
    val canBiometric = remember { Biometrics.available(context) } && biometricOn

    fun promptBiometric() {
        val activity = context.findFragmentActivity() ?: return
        Biometrics.prompt(activity, "Unlock Nook", onSuccess = { haptics.confirm(); vm.biometricSuccess() })
    }

    LaunchedEffect(canBiometric) { if (canBiometric) promptBiometric() else runCatching { focus.requestFocus() } }
    BackHandler { (context as? android.app.Activity)?.moveTaskToBack(true) }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        AnimatedGradient(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(Spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.breathingGlow(c.accent, 100.dp)) {
                Avatar(me?.photoUrl, me?.name ?: "N", 88.dp)
            }
            Spacer(Modifier.height(Spacing.lg))
            Text("Welcome back", style = NookTheme.type.display, color = c.text)
            Text(me?.let { "@${it.username}" } ?: "Enter your password", style = NookTheme.type.body, color = c.textMuted)
            Spacer(Modifier.height(Spacing.xxl))
            Box(Modifier.graphicsLayer { translationX = shake.value }) {
                NookTextField(
                    value = s.input,
                    onValueChange = vm::onInput,
                    placeholder = "Password or PIN",
                    leadingIcon = Icons.Outlined.Lock,
                    isError = s.error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = {
                        vm.submit { haptics.reject(); scope.launch { shakeIt(shake) } }
                    }),
                    trailing = if (canBiometric) {
                        { NookIconButton(Icons.Outlined.Fingerprint, "Use fingerprint", ::promptBiometric, size = 36.dp, tint = c.accent) }
                    } else null,
                    modifier = Modifier.focusRequester(focus),
                )
            }
            FieldHint(if (s.error) "That's not it — try again" else " ", isError = s.error)
            Spacer(Modifier.weight(1f))
            NookButton("Unlock", onClick = { vm.submit { haptics.reject(); scope.launch { shakeIt(shake) } } }, loading = s.checking, enabled = s.input.isNotEmpty())
            Spacer(Modifier.height(Spacing.xs))
            NookButton("Forgot password?", onClick = { confirmForgot = true }, style = NookButtonStyle.Ghost)
        }
    }
    if (confirmForgot) {
        NookConfirmDialog(
            title = "Reset your lock?",
            message = "You'll be signed out. Sign back in with Google to set a new password. Your chats stay safe in the cloud.",
            confirmLabel = "Sign out",
            destructive = true,
            onConfirm = { confirmForgot = false; vm.forgot(context, onSignedOut) },
            onDismiss = { confirmForgot = false },
        )
    }
}

private suspend fun shakeIt(a: Animatable<Float, AnimationVector1D>) {
    for (x in listOf(-28f, 24f, -18f, 12f, -6f, 0f)) a.animateTo(x, spring(dampingRatio = 0.4f, stiffness = 2200f))
}
