package com.mdhasibulislam.pingchat.ui.theme

import androidx.compose.ui.graphics.Color

// Exact tokens from the web app (css/app.css :root / [data-theme="dark"]).
val Emerald = Color(0xFF059669)
val EmeraldHover = Color(0xFF047857)
val EmeraldBright = Color(0xFF10B981)
val EmeraldSoft = Color(0xFFD1FAE5)

val LightBg = Color(0xFFF6F7F9)
val LightCard = Color(0xFFFFFFFF)
val LightCard2 = Color(0xFFF1F5F4)
val LightText = Color(0xFF0F172A)
val LightText2 = Color(0xFF334155)
val LightMuted = Color(0xFF64748B)
val LightBorder = Color(0xFFE2E8F0)
val LightDanger = Color(0xFFDC2626)
val LightDangerSoft = Color(0xFFFEE2E2)
val LightInputBg = Color(0xFFFFFFFF)
val LightBubbleThem = Color(0xFFFFFFFF)

val DarkBg = Color(0xFF0F172A)
val DarkCard = Color(0xFF1E293B)
val DarkCard2 = Color(0xFF172033)
val DarkText = Color(0xFFF1F5F9)
val DarkText2 = Color(0xFFCBD5E1)
val DarkMuted = Color(0xFF94A3B8)
val DarkBorder = Color(0xFF334155)
val DarkDanger = Color(0xFFF87171)
val DarkDangerSoft = Color(0xFF450A0A)
val DarkInputBg = Color(0xFF0F172A)
val DarkBubbleThem = Color(0xFF1E293B)
val DarkPrimarySoft = Color(0xFF064E3B)

// Avatar fallback colours (same list + hash as the web app's colorFor()).
val AvatarColors = listOf(
    Color(0xFF059669), Color(0xFF0D9488), Color(0xFF0891B2), Color(0xFF0284C7),
    Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFDC2626),
    Color(0xFFD97706), Color(0xFF65A30D)
)

fun colorFor(key: String?): Color {
    var n = 0L
    val s = key ?: "?"
    for (c in s) n = (n * 31 + c.code) and 0xFFFFFFFFL
    return AvatarColors[(n % AvatarColors.size).toInt()]
}

fun initials(name: String?): String {
    val parts = (name ?: "?").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    val first = parts.first().firstOrNull() ?: '?'
    val last = if (parts.size > 1) parts.last().firstOrNull() ?: ' ' else ' '
    return "$first$last".trim().uppercase()
}
