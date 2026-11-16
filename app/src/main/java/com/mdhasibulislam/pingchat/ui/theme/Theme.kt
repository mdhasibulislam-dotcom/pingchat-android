package com.mdhasibulislam.pingchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Extra PingChat colours beyond Material3's scheme. */
data class PingChatColors(
    val bg: Color,
    val card: Color,
    val card2: Color,
    val text: Color,
    val text2: Color,
    val muted: Color,
    val border: Color,
    val primarySoft: Color,
    val dangerSoft: Color,
    val inputBg: Color,
    val bubbleMe: Color,
    val bubbleMeText: Color,
    val bubbleThem: Color
)

private val LightPingChatColors = PingChatColors(
    bg = LightBg,
    card = LightCard,
    card2 = LightCard2,
    text = LightText,
    text2 = LightText2,
    muted = LightMuted,
    border = LightBorder,
    primarySoft = EmeraldSoft,
    dangerSoft = LightDangerSoft,
    inputBg = LightInputBg,
    bubbleMe = Emerald,
    bubbleMeText = Color.White,
    bubbleThem = LightBubbleThem
)

private val DarkPingChatColors = PingChatColors(
    bg = DarkBg,
    card = DarkCard,
    card2 = DarkCard2,
    text = DarkText,
    text2 = DarkText2,
    muted = DarkMuted,
    border = DarkBorder,
    primarySoft = DarkPrimarySoft,
    dangerSoft = DarkDangerSoft,
    inputBg = DarkInputBg,
    bubbleMe = Emerald,
    bubbleMeText = Color.White,
    bubbleThem = DarkBubbleThem
)

val LocalPingChatColors = staticCompositionLocalOf { LightPingChatColors }

private val LightScheme = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoft,
    background = LightBg,
    surface = LightCard,
    onBackground = LightText,
    onSurface = LightText,
    error = LightDanger
)

private val DarkScheme = darkColorScheme(
    primary = EmeraldBright,
    onPrimary = Color.White,
    primaryContainer = DarkPrimarySoft,
    background = DarkBg,
    surface = DarkCard,
    onBackground = DarkText,
    onSurface = DarkText,
    error = DarkDanger
)

@Composable
fun PingChatTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkPingChatColors else LightPingChatColors
    val scheme = if (darkTheme) DarkScheme else LightScheme
    CompositionLocalProvider(LocalPingChatColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = PingChatTypography, content = content)
    }
}

/** Shorthand to read PingChat colours inside a composable. */
val pingChatColors: PingChatColors
    @Composable
    get() = LocalPingChatColors.current
