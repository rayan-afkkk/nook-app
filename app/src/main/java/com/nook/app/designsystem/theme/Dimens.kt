package com.nook.app.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing scale: 4 / 8 / 12 / 16 / 20 / 24 / 32. */
object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Horizontal screen gutter. */
    val gutter = 20.dp
}

object NookShapes {
    val card = RoundedCornerShape(20.dp)
    val cardSmall = RoundedCornerShape(16.dp)
    val pill = RoundedCornerShape(percent = 50)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val bubble = RoundedCornerShape(20.dp)
    val tile = RoundedCornerShape(18.dp)
}

object Sizes {
    val touch = 48.dp
    val buttonHeight = 50.dp
    val hairline = 1.dp
    val avatarSm = 36.dp
    val avatarMd = 46.dp
    val avatarLg = 88.dp
}
