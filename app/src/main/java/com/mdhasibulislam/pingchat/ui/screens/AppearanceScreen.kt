package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.ui.theme.AccentBlue
import com.mdhasibulislam.pingchat.ui.theme.AccentGreen
import com.mdhasibulislam.pingchat.ui.theme.AccentOrange
import com.mdhasibulislam.pingchat.ui.theme.AccentOrangeRed
import com.mdhasibulislam.pingchat.ui.theme.AccentPink
import com.mdhasibulislam.pingchat.ui.theme.AccentPurple
import com.mdhasibulislam.pingchat.ui.theme.AccentRed
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.accentColorFor
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors

/** The 7 selectable accents: stored name + colour (v3.2.0, mockup screen 6). */
private val AccentChoices = listOf(
    "green" to AccentGreen,
    "blue" to AccentBlue,
    "purple" to AccentPurple,
    "orange" to AccentOrange,
    "red" to AccentRed,
    "pink" to AccentPink,
    "orange-red" to AccentOrangeRed
)

/** Font-size pills: label + scale (Normal 1.0, Large 1.15, Extra Large 1.3). */
private val FontChoices = listOf(
    "Normal" to 1.0f,
    "Large" to 1.15f,
    "Extra Large" to 1.3f
)

/**
 * Appearance (v3.2.0, NEW — mockup screen 6).
 *
 * Theme cards wire straight into the existing darkTheme /
 * onDarkThemeChange (MainActivity persists prefs.darkTheme). The accent
 * and font size are persisted in Prefs (accentColor / fontSize) and the
 * preview card at the bottom renders with the live selection, so the
 * user sees the effect immediately. The app-wide theme picks the saved
 * values up through MainActivity's PingChatTheme call site.
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    prefs: Prefs,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    val colors = pingChatColors
    var accentName by remember { mutableStateOf(prefs.accentColor) }
    var fontScale by remember { mutableStateOf(prefs.fontSize) }
    val selectedAccent = accentColorFor(accentName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text("Appearance", style = MaterialTheme.typography.titleLarge, color = colors.text, fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeCard(
                    label = "Light",
                    selected = !darkTheme,
                    onClick = { onDarkThemeChange(false) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.LightMode, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
                }
                ThemeCard(
                    label = "Dark",
                    selected = darkTheme,
                    onClick = { onDarkThemeChange(true) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = colors.text, modifier = Modifier.size(22.dp))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Accent Color", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((name, color) in AccentChoices) {
                    val selected = name == accentName
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) Emerald else Color.Transparent,
                                shape = CircleShape
                            )
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable {
                                accentName = name
                                prefs.accentColor = name
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Font Size", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((label, scale) in FontChoices) {
                    val selected = fontScale == scale
                    Box(
                        modifier = Modifier
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) Emerald else colors.border,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable {
                                fontScale = scale
                                prefs.fontSize = scale
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) Emerald else colors.text,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Preview card — rendered with the selected accent + font size.
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density, fontScale = fontScale)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.card, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(selectedAccent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "P",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("PingChat", style = MaterialTheme.typography.titleMedium, color = colors.text, fontWeight = FontWeight.Bold)
                    Text("This is a preview message.  12:56 PM", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/** One theme card: Light (sun) / Dark (moon), green outline when selected. */
@Composable
private fun ThemeCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = modifier
            .background(colors.card, RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Emerald else colors.border,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
