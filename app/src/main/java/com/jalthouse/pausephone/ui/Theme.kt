@file:OptIn(ExperimentalTextApi::class)

package com.jalthouse.pausephone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jalthouse.pausephone.R

/**
 * The palette. [dark] flips the app to its black version; [breatheLight] flips the
 * breathing screen to dark dots on white. Both are Compose state, so screens redraw.
 */
object Ink {
    var dark by mutableStateOf(false)
    var breatheLight by mutableStateOf(false)

    private fun pick(light: Long, black: Long) = Color(if (dark) black else light)
    private fun breathe(onBlack: Long, onWhite: Long) = Color(if (breatheLight) onWhite else onBlack)

    val paper: Color get() = pick(0xFFF5F5F2, 0xFF121211)   // the page
    val panel: Color get() = pick(0xFFFFFFFF, 0xFF1C1C1A)   // panels on the page
    val tile: Color get() = pick(0xFFECECE9, 0xFF2A2A27)    // app letter tiles
    val line: Color get() = pick(0xFFE3E3E0, 0xFF2E2E2B)    // hairlines
    val track: Color get() = pick(0xFFCFCFCB, 0xFF4A4A46)   // slider track, switch off
    val ink: Color get() = pick(0xFF1C1B19, 0xFFF4F4F2)     // text and marks
    val muted: Color get() = pick(0xFF5C5C58, 0xFFA3A39E)   // supporting text

    val night: Color get() = breathe(0xFF161614, 0xFFF5F5F2)     // breathing screen
    val mist: Color get() = breathe(0xFFF4F4F2, 0xFF1C1B19)      // dots and text on it
    val mistMuted: Color get() = breathe(0xFFA3A39E, 0xFF5C5C58) // supporting text on it
    val mistLine: Color get() = breathe(0xFF6E6E69, 0xFFCFCFCB)  // outlines on it
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
        colorScheme = if (Ink.dark) {
            darkColorScheme(
                primary = Ink.ink, onPrimary = Ink.paper,
                background = Ink.paper, onBackground = Ink.ink,
                surface = Ink.paper, onSurface = Ink.ink,
                surfaceVariant = Ink.tile, onSurfaceVariant = Ink.muted,
                outline = Ink.track,
            )
        } else {
            lightColorScheme(
                primary = Ink.ink, onPrimary = Ink.paper,
                background = Ink.paper, onBackground = Ink.ink,
                surface = Ink.paper, onSurface = Ink.ink,
                surfaceVariant = Ink.tile, onSurfaceVariant = Ink.muted,
                outline = Ink.track,
            )
        },
        typography = PauseTypography,
        content = content,
    )
}
