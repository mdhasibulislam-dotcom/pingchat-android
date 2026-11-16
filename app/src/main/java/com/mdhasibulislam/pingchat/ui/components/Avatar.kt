package com.mdhasibulislam.pingchat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.ui.theme.colorFor
import com.mdhasibulislam.pingchat.ui.theme.initials

enum class AvatarSize(val dp: Dp, val dotDp: Dp, val fontSp: Int) {
    XS(28.dp, 9.dp, 11),
    SM(40.dp, 11.dp, 14),
    MD(48.dp, 12.dp, 16),
    LG(104.dp, 16.dp, 36)
}

/**
 * Profile photo (or coloured initials fallback) with an optional online dot.
 * The dot straddles the bottom-right edge, mostly outside the photo,
 * Messenger-style — same geometry as the web app.
 */
@Composable
fun Avatar(
    profile: Profile?,
    size: AvatarSize = AvatarSize.MD,
    online: Boolean? = null,
    modifier: Modifier = Modifier
) {
    val name = profile?.fullName?.ifBlank { profile?.username } ?: "?"
    val bg = colorFor(profile?.id ?: name)
    val cardColor = MaterialTheme.colorScheme.surface
    Box(modifier = modifier.size(size.dp)) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            val url = profile?.avatarUrl
            if (!url.isNullOrBlank() && url.startsWith("https://")) {
                AsyncImage(
                    model = url,
                    contentDescription = name,
                    modifier = Modifier
                        .size(size.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = initials(name),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = size.fontSp.sp
                )
            }
        }
        if (online != null) {
            // Light ring (card colour) + coloured dot, sitting on the edge.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(size.dotDp + 4.dp)
                    .clip(CircleShape)
                    .background(cardColor)
                    .border(1.dp, cardColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(size.dotDp)
                        .clip(CircleShape)
                        .background(if (online) Color(0xFF22C55E) else Color(0xFF94A3B8))
                )
            }
        }
    }
}
