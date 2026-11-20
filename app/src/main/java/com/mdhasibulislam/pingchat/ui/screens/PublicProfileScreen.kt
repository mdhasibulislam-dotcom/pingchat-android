package com.mdhasibulislam.pingchat.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mdhasibulislam.pingchat.data.FriendState
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.ProfileViewerItem
import com.mdhasibulislam.pingchat.data.TimeFormat
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.colorFor
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Public profile (v3.2.0 restyle — same mockup visual style as the own
 * Profile screen: white cards, rounded 16dp corners, green primary
 * #059669): cover photo, avatar + online dot, name + verified badge,
 * @username, status line, bio, Message / audio call / video call buttons,
 * stats row, info card, share link + QR code, mutual friends, social
 * links, city + education, badges, profile visitors, and the user's own
 * theme colour on the header. WhatsApp-style contact actions
 * (Block/Unblock, Create a group, Mute, Shared media) render only when
 * the caller wires them (ChatScreen does; MainScreen leaves them null).
 *
 * HOST WIRING (MainScreen / parent — not this file): when opening the
 * screen for a user, call PeopleViewModel.loadPublicProfile(userId) for
 * mutual friends + visitor count and recordProfileView(userId) to record
 * the visit (self-views are ignored server-side). Pass canSeeBio /
 * canSeeLastSeen from PeopleViewModel.visibilityFor(profile, state)
 * (TODO worker A: PrivacyRepository's Bio / Last Seen settings).
 * Call buttons go through PeopleViewModel.startCall(userId, kind) after
 * the mic/camera permission check, exactly like ChatScreen.requestCall.
 *
 * Deliberately NOT here (removed by Hasibul, 2026-11-19): media gallery,
 * follower count, story highlights, profile video, pinned posts,
 * document sending, messaging yourself, channels/polls/secret chat/
 * scheduled messages/folders/global username search, Two-Step /
 * Passcode / Invites, Profile Photos privacy setting.
 */

/** Share-link base for a public profile. The web app lives at this Pages
 *  URL; "#/u/<username>" is the profile deep-link format the web app
 *  should route to a profile view (no such route exists yet — the link
 *  still opens PingChat, with the username in it, as specified). */
const val PROFILE_SHARE_BASE_URL = "https://mdhasibulislam-dotcom.github.io/pingchat/#/u/"

fun profileShareLink(username: String): String = "$PROFILE_SHARE_BASE_URL$username"

/** "#RRGGBB" theme colour, or null when unset/invalid. */
fun parseThemeColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        Color(("FF" + hex.removePrefix("#")).toLong(16))
    } catch (_: Exception) {
        null
    }
}

/** "Last seen today at 10:24 PM" / "Last seen yesterday at ..." / "Last seen Nov 16, 2026 at ...". */
fun lastSeenText(iso: String?): String {
    val instant = TimeFormat.parse(iso) ?: return "Last seen a long time ago"
    val zone = ZoneId.systemDefault()
    val date = instant.atZone(zone).toLocalDate()
    val today = Instant.now().atZone(zone).toLocalDate()
    val day = when {
        date == today -> "today"
        date == today.minusDays(1) -> "yesterday"
        else -> DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(date)
    }
    val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(instant.atZone(zone))
    return "Last seen $day at $time"
}

/**
 * Badges, derived from account age / activity only — no extra columns.
 * "Early member": joined within 30 days of the v3 launch (19 Nov 2026,
 * new Supabase project). "Veteran": account at least 180 days old.
 * "Social": 10+ friends. "Active sharer": 50+ photos/voice sent.
 */
fun profileBadges(profile: Profile, friendsCount: Int, mediaSent: Int): List<String> {
    val badges = mutableListOf<String>()
    val created = TimeFormat.parse(profile.createdAt)
    if (created != null) {
        val joined = created.atZone(ZoneId.systemDefault()).toLocalDate()
        if (!joined.isAfter(LocalDate.of(2026, 11, 19).plusDays(30))) badges += "Early member"
        if (Duration.between(created, Instant.now()).toDays() >= 180) badges += "Veteran"
    }
    if (friendsCount >= 10) badges += "Social"
    if (mediaSent >= 50) badges += "Active sharer"
    return badges
}

/** Render [text] as a black-on-white QR bitmap (ZXing core, free). */
private fun qrBitmap(text: String, size: Int = 512): Bitmap? {
    return try {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

@Composable
fun PublicProfileScreen(
    profile: Profile,
    state: FriendState,
    online: Boolean,
    canSeeBio: Boolean,
    canSeeLastSeen: Boolean,
    mutualFriends: List<Profile> = emptyList(),
    viewCount: Long = 0,
    /** Recent viewers — only the profile owner may see them (RLS); null hides the list. */
    recentViewers: List<ProfileViewerItem>? = null,
    friendsCount: Int = 0,
    mediaSent: Int = 0,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit,
    onAddFriend: () -> Unit,
    onCancelRequest: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenProfile: (Profile) -> Unit,
    // v3.2.0: split photo/voice counts for the peer, when the caller has
    // them (neither current caller does — see stats row note below).
    photosSent: Int = 0,
    voiceSent: Int = 0,
    // v3.2.0: WhatsApp-style contact actions (ChatScreen wires these;
    // null hides that row — MainScreen passes none, so no card shows).
    isBlocked: Boolean = false,
    isMuted: Boolean = false,
    sharedMediaCount: Int = 0,
    onBlockToggle: (() -> Unit)? = null,
    onCreateGroup: (() -> Unit)? = null,
    onMuteToggle: (() -> Unit)? = null,
    onSharedMedia: (() -> Unit)? = null
) {
    val colors = pingChatColors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shareLink = remember(profile.username) { profileShareLink(profile.username) }
    var showQr by remember { mutableStateOf(false) }

    val themeColor = parseThemeColor(profile.themeColor)
    val coverBrush = remember(profile.username, themeColor) {
        Brush.linearGradient(listOf(themeColor ?: Emerald, colorFor(profile.username)))
    }
    val memberSince = remember(profile.createdAt) {
        val instant = TimeFormat.parse(profile.createdAt)
        if (instant == null) ""
        else DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)
            .format(instant.atZone(ZoneId.systemDefault()))
    }
    // Split counts feed the "Active sharer" badge when mediaSent is 0.
    val mediaTotal = if (mediaSent > 0) mediaSent else photosSent + voiceSent
    val badges = remember(profile, friendsCount, mediaTotal) {
        profileBadges(profile, friendsCount, mediaTotal)
    }
    val socialLinks = remember(profile.socialLinks) {
        profile.socialLinks?.nonBlank() ?: emptyList()
    }
    val peerName = profile.fullName.ifBlank { profile.username }
    // The peer's email is not in the Profile model, so no Email row exists
    // for peers; Phone shows only to friends.
    val showPhone = state == FriendState.FRIENDS && !profile.phone.isNullOrBlank()
    val hasContactActions = onBlockToggle != null || onCreateGroup != null ||
        onMuteToggle != null || onSharedMedia != null

    fun openUrl(url: String) {
        val fixed = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fixed)))
        } catch (_: Exception) {
            // invalid URL — ignore the tap
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.bg).verticalScroll(rememberScrollState())) {
        // Cover photo (or theme-colour gradient) with the avatar overlapping.
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val coverUrl = profile.coverPhotoUrl
            if (!coverUrl.isNullOrBlank() && coverUrl.startsWith("https://")) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(coverBrush))
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 48.dp)
            ) {
                Avatar(profile = profile, size = AvatarSize.LG, online = online)
            }
        }
        Spacer(Modifier.height(56.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Name + verified badge.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.fullName.ifBlank { profile.username },
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (profile.isVerified) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = Color(0xFF1D9BF0),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            // Username, tap to copy.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString("@${profile.username}"))
                }
            ) {
                Text("@${profile.username}", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy username", tint = colors.muted, modifier = Modifier.size(14.dp))
            }
            // Green "Online" pill when online, else last seen (Last Seen setting respected).
            if (online) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Emerald).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(6.dp))
                    Text("Online", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else if (canSeeLastSeen) {
                Spacer(Modifier.height(6.dp))
                Text(lastSeenText(profile.lastSeen), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            // One-line status.
            if (!profile.statusLine.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    profile.statusLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Bio (visibility-gated).
            if (canSeeBio && !profile.bio.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    profile.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text
                )
            }
            // Badges.
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    badges.forEach { badge ->
                        AssistChip(onClick = {}, label = { Text(badge) })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // Message / audio call / video call in one line (friends only).
            when (state) {
                FriendState.FRIENDS -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onMessage,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Message")
                        }
                        OutlinedButton(onClick = onAudioCall) {
                            Icon(Icons.Default.Call, contentDescription = "Audio call", modifier = Modifier.size(18.dp))
                        }
                        OutlinedButton(onClick = onVideoCall) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video call", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                FriendState.NONE, FriendState.DECLINED -> {
                    Button(onClick = onAddFriend, modifier = Modifier.fillMaxWidth()) {
                        Text("Add friend")
                    }
                }
                FriendState.PENDING_OUT -> {
                    OutlinedButton(onClick = onCancelRequest, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel request")
                    }
                }
                FriendState.PENDING_IN -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
                        OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("Decline") }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // Stats row. NOTE: neither caller can supply the peer's Chats /
            // Photos / Voice counts (MainScreen passes only friends.size +
            // mediaSent=0; ChatScreen passes 0/0), and splitting mediaSent
            // into Photos+Voice would be invented data — so the row shows
            // the real values available at both call sites instead:
            // Friends, Mutual friends, Profile visitors.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PeerStat("$friendsCount", "Friends")
                PeerStat("${mutualFriends.size}", "Mutual")
                PeerStat("$viewCount", "Visitors")
            }

            Spacer(Modifier.height(20.dp))
            // Info card (same look as the own Profile screen).
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.card)
            ) {
                var first = true
                @Composable
                fun divider() {
                    if (!first) HorizontalDivider(color = colors.border)
                    first = false
                }
                if (canSeeBio && !profile.bio.isNullOrBlank()) {
                    divider()
                    ProfileInfoRow(icon = Icons.Default.Info, label = "About", value = profile.bio)
                }
                if (showPhone) {
                    divider()
                    ProfileInfoRow(icon = Icons.Default.Phone, label = "Phone", value = profile.phone ?: "")
                }
                divider()
                ProfileInfoRow(
                    icon = Icons.Default.Person,
                    label = "Username",
                    value = "@${profile.username}",
                    onClick = { clipboard.setText(AnnotatedString("@${profile.username}")) }
                )
                if (!profile.city.isNullOrBlank()) {
                    divider()
                    ProfileInfoRow(icon = Icons.Default.LocationOn, label = "City", value = profile.city)
                }
                if (!profile.education.isNullOrBlank()) {
                    divider()
                    ProfileInfoRow(icon = Icons.Default.School, label = "Education", value = profile.education)
                }
                if (memberSince.isNotBlank()) {
                    divider()
                    ProfileInfoRow(icon = Icons.Default.DateRange, label = "Member since", value = memberSince)
                }
                divider()
                ProfileInfoRow(
                    icon = Icons.Default.Visibility,
                    label = "Profile visitors",
                    value = if (viewCount > 0) "$viewCount" else "No visitors yet"
                )
            }

            // Mutual friends.
            if (mutualFriends.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Mutual friends (${mutualFriends.size})")
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.card)
                ) {
                    mutualFriends.forEachIndexed { index, friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProfile(friend) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(profile = friend, size = AvatarSize.SM)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    friend.fullName.ifBlank { friend.username },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("@${friend.username}", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                            }
                        }
                        if (index < mutualFriends.size - 1) HorizontalDivider(color = colors.border)
                    }
                }
            }

            // Social links — tap opens the URL in the browser.
            if (socialLinks.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Social links")
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.card)
                ) {
                    socialLinks.forEachIndexed { index, (network, url) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openUrl(url) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = colors.muted, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(network, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                                Text(
                                    url,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (index < socialLinks.size - 1) HorizontalDivider(color = colors.border)
                    }
                }
            }

            // Recent viewers — only the profile owner sees who visited.
            if (recentViewers != null && recentViewers.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Recent visitors")
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.card)
                ) {
                    recentViewers.forEachIndexed { index, item ->
                        val viewer = item.viewer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (viewer != null) Modifier.clickable { onOpenProfile(viewer) }
                                    else Modifier
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(profile = viewer, size = AvatarSize.SM)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    viewer?.fullName?.ifBlank { viewer.username } ?: "PingChat user",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    TimeFormat.fmtListTime(item.view.viewedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted
                                )
                            }
                        }
                        if (index < recentViewers.size - 1) HorizontalDivider(color = colors.border)
                    }
                }
            }

            // Share profile: copy link + QR code.
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(shareLink)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share profile")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showQr = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Show QR code")
            }

            // WhatsApp-style contact actions (v3.1.1 footer, moved inside
            // from ChatScreen): only the rows the caller wired are shown.
            if (hasContactActions) {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Contact actions")
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.card)
                ) {
                    var firstAction = true
                    @Composable
                    fun actionDivider() {
                        if (!firstAction) HorizontalDivider(color = colors.border)
                        firstAction = false
                    }
                    if (onMuteToggle != null) {
                        actionDivider()
                        ContactActionRow(
                            icon = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                            label = "Mute notifications",
                            detail = if (isMuted) "On" else "Off",
                            onClick = onMuteToggle
                        )
                    }
                    if (onCreateGroup != null) {
                        actionDivider()
                        ContactActionRow(
                            icon = Icons.Default.GroupAdd,
                            label = "Create a group with $peerName",
                            detail = null,
                            onClick = onCreateGroup
                        )
                    }
                    if (onSharedMedia != null) {
                        actionDivider()
                        ContactActionRow(
                            icon = Icons.Default.Image,
                            label = "Shared media",
                            detail = if (sharedMediaCount == 0) "No photos yet"
                            else "$sharedMediaCount photo${if (sharedMediaCount == 1) "" else "s"}",
                            onClick = onSharedMedia
                        )
                    }
                    if (onBlockToggle != null) {
                        actionDivider()
                        ContactActionRow(
                            icon = Icons.Default.Block,
                            label = if (isBlocked) "Unblock $peerName" else "Block $peerName",
                            detail = null,
                            destructive = true,
                            onClick = onBlockToggle
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // QR code dialog.
    if (showQr) {
        val qr = remember(shareLink) { qrBitmap(shareLink) }
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = {
                TextButton(onClick = { showQr = false }) { Text("Close") }
            },
            title = { Text("Scan to view profile") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (qr != null) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = "Profile QR code",
                            modifier = Modifier.size(240.dp)
                        )
                    } else {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Could not generate QR code.", color = colors.muted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(shareLink, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = pingChatColors.text)
    }
}

@Composable
private fun PeerStat(value: String, label: String) {
    val colors = pingChatColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = colors.text)
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted)
    }
}

@Composable
private fun ProfileInfoRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    val colors = pingChatColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** One WhatsApp-style contact-action row. */
@Composable
private fun ContactActionRow(
    icon: ImageVector,
    label: String,
    detail: String?,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else colors.muted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (destructive) MaterialTheme.colorScheme.error else colors.text,
            modifier = Modifier.weight(1f)
        )
        if (detail != null) {
            Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
    }
}
