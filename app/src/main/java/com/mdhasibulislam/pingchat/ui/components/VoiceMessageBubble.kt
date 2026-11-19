package com.mdhasibulislam.pingchat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdhasibulislam.pingchat.data.ChatMessage
import com.mdhasibulislam.pingchat.data.TickState
import com.mdhasibulislam.pingchat.data.VoicePlaybackManager
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.WhatsAppTickGrey
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import kotlin.math.abs
import kotlin.random.Random

/**
 * WhatsApp-style voice bubble (v3.1.0, worker D). Replaces the old
 * play-button + "Voice message • 0:32" row, which showed no playback
 * state at all.
 *
 * - Waveform: stable pseudo-random bars per message id; the already
 *   played part changes colour as it plays.
 * - Progress + total time: "0:07 / 0:32" while playing/paused, total
 *   time only before the first play.
 * - Seek: tap or drag on the waveform (only while this message is the
 *   active player — before that there is nothing to seek in).
 * - Speed button (1x / 1.5x / 2x) while this message is active.
 * - Playback is owned by the global [VoicePlaybackManager], so leaving
 *   the chat does not stop it.
 * - Deleted placeholder: handled by the CALLER (ChatScreen's voice
 *   branch, worker C): an expired voice (7+ days old, or no media URL)
 *   renders C's "এই ভয়েস মেসেজটি মুছে ফেলা হয়েছে" placeholder inline
 *   and this bubble is never composed for it — one placeholder text,
 *   no duplication.
 *
 * - No Keep row, no view-once consume: v3.1.0 removed both (worker C);
 *   a voice stays in the chat until the 7-day server rule deletes it.
 */
@Composable
fun VoiceMessageBubble(
    message: ChatMessage,
    mine: Boolean,
    timeText: String,
    tickState: TickState?,
    knownDurationSec: Int?,
    /** Chat this message belongs to — tracked in VoicePlaybackManager so
     *  the mini player on other screens can jump back to the chat. */
    conversationId: String? = null
) {
    val colors = pingChatColors
    val playback by VoicePlaybackManager.state.collectAsState()
    val probedDurations by VoicePlaybackManager.durations.collectAsState()

    val url = message.mediaUrl ?: ""
    val active = playback.messageId == message.id
    val durationMs = when {
        active && playback.durationMs > 0 -> playback.durationMs
        probedDurations[message.id] != null -> probedDurations[message.id]!! * 1000
        knownDurationSec != null -> knownDurationSec * 1000
        else -> 0
    }
    val positionMs = if (active) playback.positionMs else 0
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    // Probe the total time once, so the bubble shows it before first play.
    LaunchedEffect(message.id, url) {
        if (url.isNotBlank()) VoicePlaybackManager.ensureDuration(message.id, url)
    }

    val playedColor = if (mine) colors.bubbleMeText else Emerald
    val unplayedColor = if (mine) colors.bubbleMeText.copy(alpha = 0.35f)
    else colors.muted.copy(alpha = 0.45f)

    Column(modifier = Modifier.widthIn(min = 200.dp, max = 260.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active && playback.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 2.dp,
                    color = if (mine) colors.bubbleMeText else Emerald
                )
            } else {
                IconButton(
                    onClick = { VoicePlaybackManager.toggle(message.id, url, conversationId) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (active && playback.isPlaying) Icons.Default.Pause
                        else Icons.Default.PlayArrow,
                        contentDescription = if (active && playback.isPlaying) "Pause" else "Play voice message",
                        tint = if (mine) colors.bubbleMeText else colors.text
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                VoiceWaveform(
                    seed = message.id,
                    progress = progress,
                    playedColor = playedColor,
                    unplayedColor = unplayedColor,
                    onSeek = { fraction ->
                        if (active && durationMs > 0) {
                            VoicePlaybackManager.seekTo((fraction * durationMs).toInt())
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (active) {
                            "${VoicePlaybackManager.formatTime(positionMs)} / " +
                                VoicePlaybackManager.formatTime(durationMs)
                        } else {
                            VoicePlaybackManager.formatTime(durationMs)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = if (mine) colors.bubbleMeText.copy(alpha = 0.8f) else colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.weight(1f))
                    if (active) {
                        TextButton(
                            onClick = { VoicePlaybackManager.cycleSpeed() },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "${VoicePlaybackManager.formatSpeed(playback.speed)}×",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                color = if (mine) colors.bubbleMeText else Emerald
                            )
                        }
                    }
                }
            }
        }
        // Sent time + ticks, bottom-right (same MetaRow as text bubbles).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = WhatsAppTickGrey
            )
            if (mine && tickState != null) {
                Spacer(Modifier.width(2.dp))
                TickIcon(state = tickState, onBubble = true)
            }
        }
    }
}

/** Stable waveform bars (0.25..1.0 heights) from the message id hash. */
fun voiceWaveformBars(seed: String, count: Int = 32): List<Float> {
    var hash = 7L
    for (c in seed) hash = hash * 31 + c.code
    val rnd = Random(hash)
    return List(count) { 0.25f + rnd.nextFloat() * 0.75f }
}

/** Waveform bars; the played fraction renders in [playedColor]. */
@Composable
private fun VoiceWaveform(
    seed: String,
    progress: Float,
    playedColor: Color,
    unplayedColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val bars = remember(seed) { voiceWaveformBars(seed) }
    Canvas(
        modifier = modifier
            .height(36.dp)
            .voiceSeekGesture(onSeek)
    ) {
        val barWidth = 3.dp.toPx()
        val gap = 2.dp.toPx()
        val step = barWidth + gap
        val totalWidth = bars.size * step - gap
        var x = (size.width - totalWidth) / 2f
        bars.forEachIndexed { index, heightFraction ->
            val barHeight = (heightFraction * size.height).coerceAtLeast(4.dp.toPx())
            val played = (index + 0.5f) / bars.size <= progress
            drawRoundRect(
                color = if (played) playedColor else unplayedColor,
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
            x += step
        }
    }
}

/**
 * Tap-or-drag to seek. The down press is deliberately NOT consumed, so a
 * long-press on the waveform still opens the bubble menu (the outer
 * combinedClickable) — only a real drag consumes the moves.
 */
private fun Modifier.voiceSeekGesture(onSeek: (Float) -> Unit): Modifier = pointerInput(onSeek) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown()
            var width = size.width.toFloat()
            if (width <= 0f) width = 1f
            onSeek((down.position.x / width).coerceIn(0f, 1f))
            var dragged = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed && change.previousPressed) break
                if (!dragged && abs(change.position.x - down.position.x) > 8f) {
                    dragged = true
                }
                // Tap (no drag yet): keep seeking to the finger so a
                // slow tap still lands where the user touched. A real
                // drag consumes the moves so the bubble menu does not
                // steal them.
                if (dragged) change.consume()
                onSeek((change.position.x / width).coerceIn(0f, 1f))
            }
        }
    }
}
