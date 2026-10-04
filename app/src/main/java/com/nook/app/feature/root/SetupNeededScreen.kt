package com.nook.app.feature.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nook.app.designsystem.components.NookCard
import com.nook.app.designsystem.components.NookLogo
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing

/** Shown when the APK was built without app/google-services.json (e.g. a fresh import). */
@Composable
fun SetupNeededScreen() {
    NookTheme {
        val c = NookTheme.colors
        Column(
            Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(Spacing.gutter),
        ) {
            Spacer(Modifier.height(Spacing.xxl))
            NookLogo(Modifier.size(64.dp))
            Spacer(Modifier.height(Spacing.xl))
            Text("Almost there", style = NookTheme.type.display, color = c.text)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Nook needs its Firebase config before it can run. This build doesn't include one yet.",
                style = NookTheme.type.body, color = c.textMuted,
            )
            Spacer(Modifier.height(Spacing.xl))
            NookCard {
                Text("To finish setup", style = NookTheme.type.titleSans, color = c.text)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "1. Create a Firebase project and add an Android app with package com.nook.app.\n" +
                        "2. Download google-services.json into the app/ folder.\n" +
                        "3. Add your keys to local.properties.\n" +
                        "4. Rebuild. Full steps are in README.md.",
                    style = NookTheme.type.bodySmall, color = c.textMuted,
                )
            }
        }
    }
}
