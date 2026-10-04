package com.nook.app.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nook.app.designsystem.components.FieldHint
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookIconButton
import com.nook.app.designsystem.components.NookSwitch
import com.nook.app.designsystem.components.NookTextField
import com.nook.app.designsystem.components.NookTopBar
import com.nook.app.designsystem.components.SettingsRow
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.feature.lock.Biometrics
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SetPasswordScreen(
    change: Boolean,
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
    vm: SetPasswordViewModel = koinViewModel { parametersOf(change) },
) {
    val c = NookTheme.colors
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable = remember { Biometrics.available(context) }
    var visible by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(s.step) { runCatching { focus.requestFocus() } }
    BackHandler(enabled = s.step == PasswordStep.Confirm) { vm.back() }

    Column(Modifier.fillMaxSize().background(c.background).navigationBarsPadding().imePadding()) {
        NookTopBar(title = "", onBack = onBack, showHairline = false)
        Column(Modifier.weight(1f).padding(horizontal = Spacing.gutter)) {
            AnimatedContent(
                s.step,
                transitionSpec = { (slideInHorizontally { it / 5 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 5 } + fadeOut()) },
                label = "pwStep",
            ) { step ->
                Column {
                    Text(
                        when (step) {
                            PasswordStep.VerifyCurrent -> "Current password"
                            PasswordStep.Enter -> if (change) "New password" else "Lock your nook"
                            PasswordStep.Confirm -> "One more time"
                        },
                        style = NookTheme.type.display,
                        color = c.text,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        when (step) {
                            PasswordStep.VerifyCurrent -> "Enter your current app password first."
                            PasswordStep.Enter -> "Pick a password or PIN (6+ characters). It's stored only on this phone as a salted hash."
                            PasswordStep.Confirm -> "Type it again to make sure."
                        },
                        style = NookTheme.type.body,
                        color = c.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
            LengthDots(s.input.length)
            Spacer(Modifier.height(Spacing.lg))
            NookTextField(
                value = s.input,
                onValueChange = vm::onInput,
                placeholder = "Password or PIN",
                leadingIcon = Icons.Outlined.Lock,
                isError = s.error != null,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { vm.next(onDone) }),
                trailing = {
                    NookIconButton(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (visible) "Hide password" else "Show password",
                        onClick = { visible = !visible },
                        size = 36.dp, iconSize = 20.dp, tint = c.textMuted,
                    )
                },
                modifier = Modifier.focusRequester(focus),
            )
            FieldHint(s.error ?: "Forgot it later? Sign out and back in to reset.", isError = s.error != null)
            if (biometricAvailable && s.step == PasswordStep.Confirm) {
                Spacer(Modifier.height(Spacing.lg))
                SettingsRow(
                    icon = Icons.Outlined.Fingerprint,
                    title = "Unlock with fingerprint",
                    subtitle = "Faster than typing",
                    onClick = { vm.setBiometric(!s.useBiometric) },
                    trailing = { NookSwitch(s.useBiometric, vm::setBiometric) },
                )
            }
        }
        NookButton(
            text = if (s.step == PasswordStep.Confirm) "Save password" else "Continue",
            onClick = { vm.next(onDone) },
            loading = s.working,
            enabled = s.input.isNotEmpty(),
            modifier = Modifier.padding(Spacing.gutter),
        )
    }
}

@Composable
private fun LengthDots(length: Int) {
    val c = NookTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(maxOf(6, length).coerceAtMost(12)) { i ->
            val filled = i < length
            val scale by animateFloatAsState(if (filled) 1f else 0.6f, NookMotion.bouncy(), label = "dot")
            Box(
                Modifier.size(14.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape).background(if (filled) (if (i < 6) c.accent else c.amber) else c.border),
                contentAlignment = Alignment.Center,
            ) {}
        }
    }
}
