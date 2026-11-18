@file:OptIn(ExperimentalTextApi::class)

package com.jalthouse.pausephone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jalthouse.pausephone.R

object Ink {
    val paper = Color(0xFFF5F5F2)      // soft neutral white, the page
    val panel = Color(0xFFFFFFFF)      // panels sit on the page
    val tile = Color(0xFFECECE9)       // app letter tiles
    val line = Color(0xFFE3E3E0)       // hairlines
    val track = Color(0xFFCFCFCB)      // slider track, switch off
    val ink = Color(0xFF1C1B19)        // text and marks
    val muted = Color(0xFF5C5C58)      // supporting text
    val night = Color(0xFF161614)      // breathing screen
    val mist = Color(0xFFF4F4F2)       // dots and text on night
    val mistMuted = Color(0xFFA3A39E)  // supporting text on night
    val mistLine = Color(0xFF6E6E69)   // outlines on night
}

private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(
    manrope(200), manrope(300), manrope(400), manrope(500), manrope(600), manrope(700),
)

private fun TextStyle.m() = copy(fontFamily = Manrope)

private val base = Typography()
private val PauseTypography = Typography(
    displayLarge = base.displayLarge.m(), displayMedium = base.displayMedium.m(),
    displaySmall = base.displaySmall.m(), headlineLarge = base.headlineLarge.m(),
    headlineMedium = base.headlineMedium.m(), headlineSmall = base.headlineSmall.m(),
    titleLarge = base.titleLarge.m(), titleMedium = base.titleMedium.m(),
    titleSmall = base.titleSmall.m(), bodyLarge = base.bodyLarge.m(),
    bodyMedium = base.bodyMedium.m(), bodySmall = base.bodySmall.m(),
    labelLarge = base.labelLarge.m(), labelMedium = base.labelMedium.m(),
    labelSmall = base.labelSmall.m(),
)

/** Named text styles used across the screens. */
object Type {
    val title = TextStyle(fontFamily = Manrope, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.56).sp, lineHeight = 34.sp)
    val heading = TextStyle(fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.16).sp)
    val body = TextStyle(fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val small = TextStyle(fontFamily = Manrope, fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val stat = TextStyle(fontFamily = Manrope, fontSize = 26.sp, fontWeight = FontWeight.Light)
    val wordmark = TextStyle(fontFamily = Manrope, fontSize = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.7.sp)
    val button = TextStyle(fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun PauseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink.ink, onPrimary = Ink.paper,
            background = Ink.paper, onBackground = Ink.ink,
            surface = Ink.paper, onSurface = Ink.ink,
            surfaceVariant = Ink.tile, onSurfaceVariant = Ink.muted,
            outline = Ink.track,
        ),
        typography = PauseTypography,
        content = content,
    )
}
