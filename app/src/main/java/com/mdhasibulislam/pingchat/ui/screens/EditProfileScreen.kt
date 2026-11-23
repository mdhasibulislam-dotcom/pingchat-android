package com.mdhasibulislam.pingchat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.data.SocialLinks
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel

/** About presets (tap one to fill the About field). */
private val AboutPresets = listOf(
    "Available", "Busy", "At work", "In a meeting", "Sleeping", "Urgent calls only"
)

/**
 * Theme colour pills (v3.2.4, mockup 2): exactly the four mockup
 * options. "Default" stores no colour (blank -> NULL in Supabase) and
 * the profile header falls back to its emerald gradient; the other
 * three store a "#RRGGBB" hex in profiles.theme_color, which the
 * Profile / Public Profile / peer profile screens already use for the
 * cover gradient (parseThemeColor). Emerald (#10B981) is the current
 * default. The app-wide accent stays in Appearance (Prefs.accentColor,
 * MainActivity's PingChatTheme) — this pill is the *profile* theme
 * colour, not the app accent.
 */
private val ThemeColorPresets = listOf(
    "Default" to null,
    "Emerald" to "#10B981",
    "Blue" to "#3B82F6",
    "Purple" to "#8B5CF6"
)

/** Max length of the About field (mockup shows a live x/100 counter). */
private const val ABOUT_MAX_LENGTH = 100

/** The four explainer lines in the Private Profile bottom sheet (mockup 1). */
private val PrivateExplainerLines = listOf(
    "Your full details (phone, email, education, links) will be hidden from others.",
    "Only your profile picture, name and about will be visible.",
    "People can't see your phone number, email or other links.",
    "You can still chat with anyone, but they won't get your personal information."
)

/**
 * Edit Profile (v3.2.4, mockups 1 + 2): the whole screen scrolls
 * (verticalScroll — it is long, nothing is crammed onto one fixed
 * page); avatar + camera badge; locked username with a lock icon;
 * About with a live x/100 counter; read-only email with the Login
 * Email helper; Profile Photo / Cover Photo as two side-by-side cards
 * whose buttons really work (avatar upload / remove, cover upload to
 * the 'covers' bucket); "More details" inline fields (Status, City,
 * Education, Facebook / Instagram / GitHub URLs); Theme Colour pills;
 * mutually exclusive Public / Private Profile toggles with a green
 * state banner underneath; and the Private Profile explainer bottom
 * sheet the first time Private is turned on.
 *
 * All profile fields already exist in the DB (migration-v310-profile
 * .sql: cover_photo_url, city, education, status_line, social_links,
 * theme_color; base schema: is_public, phone) — no v3.2.4 migration
 * was needed. Username is READ-ONLY here: the only username path that
 * exists is the pingchat_is_username_available RPC (availability
 * check) — there is no username-update method in ProfileRepository /
 * ProfileViewModel. Email is display-only: changing the login email
 * stays in Privacy & Security > Login Email. Phone is a DISPLAY
 * profile field only, saved via [ProfileViewModel.saveProfile] —
 * never a login method.
 *
 * Remove Photo: asks for confirmation, then clears avatar_url to NULL
 * in Supabase via ProfileViewModel.removeAvatar (ProfileRepository
 * .clearAvatarUrl) — the photo is really removed, not just hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(viewModel: ProfileViewModel, onBack: () -> Unit) {
    val profile by viewModel.profile.collectAsState()
    val user by viewModel.user.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val uploading by viewModel.uploading.collectAsState()
    val uploadingCover by viewModel.uploadingCover.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.info.collectAsState()
    val colors = pingChatColors
    val context = LocalContext.current
    // Plain boolean flag only (no Compose state inside Prefs for this
    // key), so a second Prefs instance here is safe — MainActivity owns
    // the instance that drives the live theme, this one only reads /
    // writes the explainer flag.
    val prefs = remember { Prefs(context.applicationContext) }

    var name by remember(profile?.fullName) { mutableStateOf(profile?.fullName ?: "") }
    var bio by remember(profile?.bio) { mutableStateOf(profile?.bio ?: "") }
    var phone by remember(profile?.phone) { mutableStateOf(profile?.phone ?: "") }
    var isPublic by remember(profile?.isPublic) { mutableStateOf(profile?.isPublic ?: true) }
    // v3.1.0 extended fields (kept below the mockup fields).
    var city by remember(profile?.city) { mutableStateOf(profile?.city ?: "") }
    var education by remember(profile?.education) { mutableStateOf(profile?.education ?: "") }
    var statusLine by remember(profile?.statusLine) { mutableStateOf(profile?.statusLine ?: "") }
    var facebook by remember(profile?.socialLinks) { mutableStateOf(profile?.socialLinks?.facebook ?: "") }
    var instagram by remember(profile?.socialLinks) { mutableStateOf(profile?.socialLinks?.instagram ?: "") }
    var github by remember(profile?.socialLinks) { mutableStateOf(profile?.socialLinks?.github ?: "") }
    var themeColor by remember(profile?.themeColor) { mutableStateOf(profile?.themeColor ?: "") }
    var localError by remember { mutableStateOf<String?>(null) }
    var showRemovePhotoConfirm by remember { mutableStateOf(false) }
    var showPrivateExplainer by remember { mutableStateOf(false) }

    fun pickBytes(uri: Uri?): Triple<ByteArray, String, String>? {
        if (uri == null) return null
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return null
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        return Triple(bytes, ext, mime)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        pickBytes(uri)?.let { (bytes, ext, mime) -> viewModel.uploadAvatar(bytes, ext, mime) }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        pickBytes(uri)?.let { (bytes, ext, mime) -> viewModel.uploadCover(bytes, ext, mime) }
    }

    /**
     * The two visibility toggles share one boolean (is_public): turning
     * one on turns the other off. Going private shows the explainer
     * bottom sheet the FIRST time only (Prefs flag); it is not shown
     * when the profile was already private on entry.
     */
    fun setPublic(value: Boolean) {
        val wasPublic = isPublic
        isPublic = value
        if (!value && wasPublic && !prefs.privateProfileExplainerShown) {
            showPrivateExplainer = true
        }
    }

    fun save() {
        if (name.trim().length < 2) {
            localError = "Enter your full name."
            return
        }
        localError = null
        viewModel.saveProfile(name, bio, phone, isPublic)
        viewModel.saveExtendedProfile(
            city,
            education,
            statusLine,
            SocialLinks(
                facebook = facebook.trim().ifBlank { null },
                instagram = instagram.trim().ifBlank { null },
                github = github.trim().ifBlank { null }
            ),
            themeColor
        )
        onBack()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(colors.bg).verticalScroll(rememberScrollState())
    ) {
        // Top bar: back arrow, title, green pill Save button top-right.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Text(
                "Edit Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = ::save,
                enabled = !saving,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar with green camera badge (tap = change photo).
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(profile = profile, size = AvatarSize.LG)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Emerald)
                        .clickable { picker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (uploading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            // Fields with leading icons (mockup screen 2).
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = "@${profile?.username ?: ""}",
                onValueChange = {},
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                // Mockup 1: locked username shows a lock icon.
                trailingIcon = { Icon(Icons.Default.Lock, contentDescription = "Locked") },
                supportingText = { Text("Username can't be changed") },
                singleLine = true,
                enabled = false,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(ABOUT_MAX_LENGTH) },
                label = { Text("About") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                supportingText = { Text("${bio.length}/$ABOUT_MAX_LENGTH") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AboutPresets.forEach { preset ->
                    AssistChip(onClick = { bio = preset }, label = { Text(preset) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = user?.email ?: "",
                onValueChange = {},
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                supportingText = { Text("Change your login email in Privacy & Security > Login Email") },
                singleLine = true,
                enabled = false,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Profile Photo / Cover Photo as two side-by-side cards
            // (mockup 2). Both cards' buttons really work: Change Photo
            // / Remove use the avatar upload / remove flow, Change Cover
            // picks an image and uploads it to the 'covers' bucket.
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.card)
                        .padding(16.dp)
                ) {
                    Text("Profile Photo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Change or remove your profile picture",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { picker.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change Photo", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (profile?.avatarUrl.isNullOrBlank()) {
                                localError = "You don't have a profile photo to remove."
                            } else {
                                showRemovePhotoConfirm = true
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove")
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.card)
                        .padding(16.dp)
                ) {
                    Text("Cover Photo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.text)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Change your cover photo",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                    Spacer(Modifier.height(12.dp))
                    if (!profile?.coverPhotoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile?.coverPhotoUrl,
                            contentDescription = "Current cover photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.card2),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = colors.muted, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { coverPicker.launch("image/*") },
                        enabled = !uploadingCover,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uploadingCover) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Change Cover", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // v3.1.0 extended fields (below the mockup fields).
            Spacer(Modifier.height(20.dp))
            SectionTitle("More details")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = statusLine,
                onValueChange = { statusLine = it },
                label = { Text("Status (one line — what are you doing now?)") },
                leadingIcon = { Icon(Icons.Default.ShortText, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = education,
                onValueChange = { education = it },
                label = { Text("Education") },
                leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = facebook,
                onValueChange = { facebook = it },
                label = { Text("Facebook URL") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = instagram,
                onValueChange = { instagram = it },
                label = { Text("Instagram URL") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = github,
                onValueChange = { github = it },
                label = { Text("GitHub URL") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Theme Colour pills (mockup 2): Default / Emerald / Blue /
            // Purple. The choice is saved with the profile (Save button)
            // as profiles.theme_color and colours the profile header.
            Spacer(Modifier.height(20.dp))
            SectionTitle("Theme Colour")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeColorPresets.forEach { (label, hex) ->
                    val selected = if (hex == null) themeColor.isBlank()
                    else themeColor.equals(hex, ignoreCase = true)
                    ThemeColourPill(
                        label = label,
                        hex = hex,
                        selected = selected,
                        onClick = { themeColor = hex ?: "" }
                    )
                }
            }

            // Public / Private Profile (mockup 2): mutually exclusive —
            // both read the one is_public flag, so turning one on turns
            // the other off. Saved with the profile (Save button).
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Public Profile", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    Text(
                        "Anyone can see your full profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
                Switch(checked = isPublic, onCheckedChange = { setPublic(it) })
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Private Profile", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    Text(
                        "Only friends can see your profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
                Switch(checked = !isPublic, onCheckedChange = { setPublic(!it) })
            }

            // Green state banner (mockup 2): states the current visibility.
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Emerald)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isPublic) Icons.Default.Public else Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    if (isPublic) "Your profile is public now. Everyone can see your profile."
                    else "Your profile is private now. Only your friends can see your full profile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val shownError = localError ?: error
            if (shownError != null) {
                Spacer(Modifier.height(12.dp))
                Text(shownError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (info != null) {
                Spacer(Modifier.height(12.dp))
                Text(info ?: "", color = Emerald, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // Remove-photo confirmation (v3.2.0): confirming clears avatar_url
    // to NULL in Supabase and the local profile.
    if (showRemovePhotoConfirm) {
        AlertDialog(
            onDismissRequest = { showRemovePhotoConfirm = false },
            title = { Text("Remove profile photo?") },
            text = { Text("Your profile photo will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemovePhotoConfirm = false
                        viewModel.removeAvatar()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePhotoConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Private Profile explainer (mockup 1): shown the first time Private
    // is turned on. Dismissing by any means (Got it, swipe, back) marks
    // it shown in Prefs so it never nags again.
    if (showPrivateExplainer) {
        ModalBottomSheet(
            onDismissRequest = {
                prefs.privateProfileExplainerShown = true
                showPrivateExplainer = false
            },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text(
                    "Private Profile — What happens when it's turned on?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = pingChatColors.text
                )
                Spacer(Modifier.height(16.dp))
                PrivateExplainerLines.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Emerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = pingChatColors.text)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        prefs.privateProfileExplainerShown = true
                        showPrivateExplainer = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Got it", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pingChatColors.text)
    }
}

/**
 * One Theme Colour pill: colour dot + label, with a check mark when
 * selected. "Default" (hex = null) uses a grey dot — no colour stored.
 */
@Composable
private fun ThemeColourPill(
    label: String,
    hex: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = pingChatColors
    val dotColor = parseThemeColor(hex) ?: colors.muted
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) dotColor else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) dotColor else colors.border,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Color.White else colors.text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
