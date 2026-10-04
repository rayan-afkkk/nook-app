package com.nook.app.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.FieldHint
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookTextField
import com.nook.app.designsystem.components.breathingGlow
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun UsernameScreen(onDone: () -> Unit, vm: UsernameViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val s by vm.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
    ) {
        Text("Pick your name", style = NookTheme.type.display, color = c.text)
        Spacer(Modifier.height(Spacing.xs))
        Text("Your @username is how friends find you. It's permanent, so make it yours.", style = NookTheme.type.body, color = c.textMuted)
        Spacer(Modifier.height(Spacing.xxl))
        Box(Modifier.breathingGlow(c.peach, 90.dp)) {
            Avatar(s.photoUrl, s.displayName.ifBlank { s.username }, 88.dp)
        }
        Spacer(Modifier.height(Spacing.xl))
        NookTextField(
            value = s.displayName,
            onValueChange = vm::onDisplayName,
            placeholder = "Display name",
            leadingIcon = Icons.Outlined.Person,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(Spacing.sm))
        NookTextField(
            value = s.username,
            onValueChange = vm::onUsername,
            placeholder = "username",
            leadingIcon = Icons.Outlined.AlternateEmail,
            isError = s.availability is Availability.Invalid || s.availability == Availability.Taken,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            trailing = {
                AnimatedContent(
                    s.availability,
                    transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut() },
                    label = "avail",
                ) { a ->
                    when (a) {
                        Availability.Checking -> CircularProgressIndicator(Modifier.size(18.dp), color = c.textMuted, strokeWidth = 2.dp)
                        Availability.Available -> Icon(Icons.Rounded.CheckCircle, "Available", tint = c.online, modifier = Modifier.size(20.dp))
                        Availability.Taken, is Availability.Invalid -> Icon(Icons.Rounded.Cancel, "Unavailable", tint = c.danger, modifier = Modifier.size(20.dp))
                        else -> Spacer(Modifier.size(20.dp))
                    }
                }
            },
        )
        val hint = when (val a = s.availability) {
            Availability.Available -> "@${s.username} is yours"
            Availability.Taken -> "Someone already has that one"
            is Availability.Invalid -> a.reason
            Availability.Offline -> "You're offline — we'll check when you're back"
            else -> "3–20 characters: a–z, 0–9, _ and ."
        }
        FieldHint(hint, isError = s.availability is Availability.Invalid || s.availability == Availability.Taken)
        if (s.error != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(s.error.orEmpty(), style = NookTheme.type.bodySmall, color = c.danger)
        }
        Spacer(Modifier.height(Spacing.xxl))
        NookButton("Continue", onClick = { vm.submit(onDone) }, enabled = s.canSubmit, loading = s.saving)
    }
}
