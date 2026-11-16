package com.mdhasibulislam.pingchat.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.TickState
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors

/**
 * Read ticks, WhatsApp-style, inside the bubble (bottom-right, small).
 * - SENT: single tick (sent, peer offline / no notification yet).
 * - DELIVERED: double tick (reached the peer's phone).
 * - SEEN: tick inside a round badge (peer actually looked at the message).
 */
@Composable
fun TickIcon(state: TickState, onBubble: Boolean = true, modifier: Modifier = Modifier) {
    val tickTint = if (onBubble) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    when (state) {
        TickState.SENT -> Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Sent",
            modifier = modifier.size(15.dp),
            tint = tickTint
        )
        TickState.DELIVERED -> Box(modifier = modifier.size(19.dp)) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Delivered",
                modifier = Modifier.size(15.dp),
                tint = tickTint
            )
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier
                    .size(15.dp)
                    .offset(x = 5.dp),
                tint = tickTint
            )
        }
        TickState.SEEN -> {
            // Round badge: white circle + emerald tick on the bubble,
            // emerald circle + white tick elsewhere.
            val badgeBg = if (onBubble) Color.White else Emerald
            val badgeTick = if (onBubble) Emerald else Color.White
            Box(
                modifier = modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Seen",
                    modifier = Modifier.size(11.dp),
                    tint = badgeTick
                )
            }
        }
    }
}

/**
 * Typing indicator — the peer's small avatar + three waving dots, no name
 * (Messenger-style, same as the web app).
 */
@Composable
fun TypingIndicator(peer: Profile?, modifier: Modifier = Modifier) {
    val colors = pingChatColors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Avatar(profile = peer, size = AvatarSize.XS)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(colors.card2)
                .border(1.dp, colors.border, RoundedCornerShape(999.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                WavingDot(delayMillis = index * 150)
            }
        }
    }
}

@Composable
private fun WavingDot(delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "typing")
    val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, delayMillis = delayMillis),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .offset(y = offsetY.dp)
            .size(7.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.7f))
    )
}
