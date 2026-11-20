package com.mdhasibulislam.pingchat.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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

/** Theme colour presets for the public profile header (v3.1.0). */
private val ThemeColorPresets = listOf(
    "Default" to null,
    "Emerald" to "#10B981",
    "Blue" to "#3B82F6",
    "Purple" to "#8B5CF6",
    "Pink" to "#EC4899",
    "Orange" to "#F97316"
)

/** Max length of the About field (mockup shows a live x/100 counter). */
private const val ABOUT_MAX_LENGTH = 100

/**
 * Edit Profile (v3.2.0, mockup screen 2): avatar + camera badge, fields
 * with leading icons, Profile Photo / Cover Photo sections, then the
 * v3.1.0 extended fields below the mockup fields so nothing is lost.
 *
 * Username is READ-ONLY here: the only username path that exists is the
 * pingchat_is_username_available RPC (availability check) — there is no
 * username-update method in ProfileRepository / ProfileViewModel, and
 * this file may not add a backend write path. Email is display-only:
 * changing the login email stays in Privacy & Security > Login Email.
 * Phone is a DISPLAY profile field only, saved via [ProfileViewModel.saveProfile]
 * — never a login method.
 *
 * Remove Photo: asks for confirmation, then clears avatar_url to NULL
 * in Supabase via ProfileViewModel.removeAvatar (ProfileRepository
 * .clearAvatarUrl) — the photo is really removed, not just hidden.
 */
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

            // Profile Photo section.
            Spacer(Modifier.height(20.dp))
            SectionTitle("Profile Photo")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.card)
            ) {
                EditActionRow(
                    icon = Icons.Default.Image,
                    label = "Change Photo",
                    onClick = { picker.launch("image/*") }
                )
                HorizontalDivider(color = colors.border)
                // v3.2.0: really removes the photo (confirm dialog, then
                // avatar_url is set to NULL in Supabase).
                EditActionRow(
                    icon = Icons.Default.Delete,
                    label = "Remove Photo",
                    destructive = true,
                    onClick = {
                        if (profile?.avatarUrl.isNullOrBlank()) {
                            localError = "You don't have a profile photo to remove."
                        } else {
                            showRemovePhotoConfirm = true
                        }
                    }
                )
            }

            // Cover Photo section.
            Spacer(Modifier.height(20.dp))
            SectionTitle("Cover Photo")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.card)
            ) {
                EditActionRow(
                    icon = Icons.Default.Image,
                    label = "Change Cover",
                    trailing = if (uploadingCover) "Uploading…" else null,
                    onClick = { coverPicker.launch("image/*") }
                )
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
            Spacer(Modifier.height(12.dp))
            Text("Theme colour", style = MaterialTheme.typography.bodyMedium, color = colors.text, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeColorPresets.forEach { (label, hex) ->
                    AssistChip(
                        onClick = { themeColor = hex ?: "" },
                        label = { Text(label) },
                        leadingIcon = {
                            if (hex != null) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(parseThemeColor(hex) ?: Color.Gray)
                                )
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Public profile", style = MaterialTheme.typography.bodyMedium, color = colors.text)
                    Text(
                        "Anyone can see your full profile. Private profiles show full info only to friends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                }
                Switch(checked = isPublic, onCheckedChange = { isPublic = it })
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
}

@Composable
private fun SectionTitle(title: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pingChatColors.text)
    }
}

/** One row in the Profile Photo / Cover Photo cards. */
@Composable
private fun EditActionRow(
    icon: ImageVector,
    label: String,
    trailing: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val colors = pingChatColors
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
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
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (destructive) MaterialTheme.colorScheme.error else colors.text,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
    }
}
