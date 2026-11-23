package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors

/**
 * Terms & Conditions — v3.2.4, modelled on PrivacyPolicyScreen.
 * A scrollable read-only view of the terms; the text lives in
 * TermsConditionsText.kt.
 *
 * Opened from Settings > Privacy & Security > "Terms & Conditions"
 * (route "terms_conditions").
 */
@Composable
fun TermsConditionsScreen(onBack: () -> Unit) {
    val colors = pingChatColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Header, same pattern as PrivacyPolicyScreen: back button + title.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Terms & Conditions",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = "Last updated: November 24, 2026 · Version 3.2.4",
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = TermsConditionsText.TEXT.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
