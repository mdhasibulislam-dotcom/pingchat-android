package com.mdhasibulislam.pingchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdhasibulislam.pingchat.data.VoicePlaybackManager
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors

/**
 * Mini player (v3.1.0, worker D): sits at the top of the screen while a
 * voice message plays — even after leaving the chat — with pause/resume,
 * progress and a close (stop) button. Tap it to jump back to the
 * message ([onTap]).
 *
 * Reads the global [VoicePlaybackManager] state directly, so it works
 * from any screen once it is placed there. Wired into ChatScreen now;
 * placing it on other screens (MainScreen / MainActivity) is a
 * follow-up for the parent — this file owns only the component.
 * Renders nothing when nothing is playing.
 */
@Composable
fun VoiceMiniPlayer(
    label: String,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onClose: () -> Unit = { VoicePlaybackManager.stop() }
) {
    val colors = pingChatColors
    val state by VoicePlaybackManager.state.collectAsState()
    if (state.messageId == null) return

    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.card)
            .clickable { onTap() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Emerald)
                .clickable {
                    if (state.isPlaying) VoicePlaybackManager.pause()
                    else VoicePlaybackManager.resume()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Resume",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${VoicePlaybackManager.formatTime(state.positionMs)} / " +
                    VoicePlaybackManager.formatTime(state.durationMs),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = colors.muted
            )
            Spacer(Modifier.height(4.dp))
            // Progress bar (plain Boxes: no version-sensitive
            // LinearProgressIndicator overloads).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.border)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Emerald)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Stop playback",
                tint = colors.muted
            )
        }
    }
}
