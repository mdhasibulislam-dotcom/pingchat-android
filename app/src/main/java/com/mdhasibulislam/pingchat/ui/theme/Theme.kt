package com.mdhasibulislam.pingchat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Extra PingChat colours beyond Material3's scheme. */
data class PingChatColors(
    val bg: Color,
    val card: Color,
    val card2: Color,
    val text: Color,
    val text2: Color,
    val muted: Color,
    val border: Color,
    val primary: Color,
    val primarySoft: Color,
    val dangerSoft: Color,
    val inputBg: Color,
    val bubbleMe: Color,
    val bubbleMeText: Color,
    val bubbleThem: Color,
    val chatBg: Color
)

private val LightPingChatColors = PingChatColors(
    bg = LightBg,
    card = LightCard,
    card2 = LightCard2,
    text = LightText,
    text2 = LightText2,
    muted = LightMuted,
    border = LightBorder,
    primary = Emerald,
    primarySoft = EmeraldSoft,
    dangerSoft = LightDangerSoft,
    inputBg = LightInputBg,
    bubbleMe = WhatsAppOutgoingLight,
    bubbleMeText = Color(0xFF111B21),
    bubbleThem = WhatsAppIncomingLight,
    chatBg = WhatsAppChatBgLight
)

private val DarkPingChatColors = PingChatColors(
    bg = DarkBg,
    card = DarkCard,
    card2 = DarkCard2,
    text = DarkText,
    text2 = DarkText2,
    muted = DarkMuted,
    border = DarkBorder,
    primary = Emerald,
    primarySoft = DarkPrimarySoft,
    dangerSoft = DarkDangerSoft,
    inputBg = DarkInputBg,
    bubbleMe = WhatsAppOutgoingDark,
    bubbleMeText = Color.White,
    bubbleThem = WhatsAppIncomingDark,
    chatBg = WhatsAppChatBgDark
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

/**
 * v3.2.0: [accent] becomes MaterialTheme's primary (light + dark
 * schemes) and PingChatColors.primary, so buttons and accents follow
 * the Appearance choice; [fontScale] scales all text via LocalDensity.
 *
 * Scope note: screens that reference Emerald / EmeraldBright directly
 * stay green — only MaterialTheme primary and the new screens follow
 * the accent. That is the accepted v3.2.0 scope.
 */
@Composable
fun PingChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = Emerald,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val baseColors = if (darkTheme) DarkPingChatColors else LightPingChatColors
    val colors = baseColors.copy(primary = accent)
    val scheme = if (darkTheme) DarkScheme.copy(primary = accent)
    else LightScheme.copy(primary = accent)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalPingChatColors provides colors,
        LocalDensity provides Density(density = density.density, fontScale = fontScale)
    ) {
        MaterialTheme(colorScheme = scheme, typography = PingChatTypography, content = content)
    }
}

/** Map a stored accent name (Prefs.accentColor) to its Color. Unknown names fall back to green. */
fun accentColorFor(name: String): Color = when (name) {
    "blue" -> AccentBlue
    "purple" -> AccentPurple
    "orange" -> AccentOrange
    "red" -> AccentRed
    "pink" -> AccentPink
    "orange-red" -> AccentOrangeRed
    else -> AccentGreen
}

/** Shorthand to read PingChat colours inside a composable. */
val pingChatColors: PingChatColors
    @Composable
    get() = LocalPingChatColors.current
