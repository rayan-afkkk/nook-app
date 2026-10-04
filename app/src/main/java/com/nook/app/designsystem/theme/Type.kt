package com.nook.app.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nook.app.R

val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

@OptIn(ExperimentalTextApi::class)
private fun dmSans(weight: Int) = Font(
    R.font.dm_sans_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val DmSans = FontFamily(dmSans(400), dmSans(500), dmSans(600), dmSans(700))

@Immutable
data class NookTypography(
    /** Big screen titles, ~40sp, serif, left aligned. */
    val display: TextStyle,
    /** Onboarding / hero headlines. */
    val hero: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val titleSans: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
)

val DefaultNookTypography = NookTypography(
    display = TextStyle(fontFamily = InstrumentSerif, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em),
    hero = TextStyle(fontFamily = InstrumentSerif, fontSize = 48.sp, lineHeight = 50.sp, letterSpacing = (-0.015).em),
    headline = TextStyle(fontFamily = InstrumentSerif, fontSize = 30.sp, lineHeight = 34.sp),
    title = TextStyle(fontFamily = InstrumentSerif, fontSize = 24.sp, lineHeight = 28.sp),
    titleSans = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    body = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyStrong = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp),
    label = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.01.em),
    caption = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 15.sp),
    button = TextStyle(fontFamily = DmSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
)

val LocalNookTypography = staticCompositionLocalOf { DefaultNookTypography }
