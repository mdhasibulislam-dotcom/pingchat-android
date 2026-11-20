package com.mdhasibulislam.pingchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.ChatRepository
import com.mdhasibulislam.pingchat.data.FriendsRepository
import com.mdhasibulislam.pingchat.data.ProfileRepository
import com.mdhasibulislam.pingchat.data.ProfileViewerItem
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.SocialLinks
import com.mdhasibulislam.pingchat.data.Supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** My profile + settings. Mirrors js/profile.js + js/settings.js. */
class ProfileViewModel : ViewModel() {
    private val repo = ProfileRepository()
    private val authRepo = AuthRepository()

    val profile = SessionManager.profile
    val user = SessionManager.user

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val _uploadingCover = MutableStateFlow(false)
    val uploadingCover: StateFlow<Boolean> = _uploadingCover.asStateFlow()

    /** v3.1.0: how many distinct users viewed my profile. */
    private val _viewCount = MutableStateFlow(0L)
    val viewCount: StateFlow<Long> = _viewCount.asStateFlow()

    /** v3.1.0: most recent viewers of my profile (owner-only, RLS). */
    private val _recentViewers = MutableStateFlow<List<ProfileViewerItem>>(emptyList())
    val recentViewers: StateFlow<List<ProfileViewerItem>> = _recentViewers.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info.asStateFlow()

    private val _friendsCount = MutableStateFlow(0)
    val friendsCount: StateFlow<Int> = _friendsCount.asStateFlow()

    private val _chatsCount = MutableStateFlow(0)
    val chatsCount: StateFlow<Int> = _chatsCount.asStateFlow()

    private val _photosSent = MutableStateFlow(0)
    val photosSent: StateFlow<Int> = _photosSent.asStateFlow()

    private val _voiceSent = MutableStateFlow(0)
    val voiceSent: StateFlow<Int> = _voiceSent.asStateFlow()

    init {
        loadStats()
        loadVisitors()
    }

    /** v3.1.0: visitor count + recent viewers for my own profile. */
    fun loadVisitors() {
        viewModelScope.launch {
            val me = repo.myId() ?: return@launch
            try {
                _viewCount.value = repo.profileViewCount(me)
                _recentViewers.value = repo.loadRecentViewers(me)
            } catch (e: Exception) {
                // visitors are best-effort (e.g. migration not run yet)
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            try {
                _friendsCount.value = FriendsRepository().loadFriends().size
            } catch (e: Exception) {
                // stats are best-effort
            }
            try {
                val chatRepo = ChatRepository()
                val convs = chatRepo.loadConversations()
                _chatsCount.value = convs.size
                // Media shared: photos + voice messages I sent, across chats.
                var photos = 0
                var voice = 0
                val me = repo.myId()
                for (item in convs) {
                    try {
                        for (m in chatRepo.loadMessages(item.conversation.id)) {
                            if (m.senderId == me) {
                                if (m.kind == "photo") photos++
                                if (m.kind == "voice") voice++
                            }
                        }
                    } catch (e: Exception) {
                        // one chat's messages are best-effort
                    }
                }
                _photosSent.value = photos
                _voiceSent.value = voice
            } catch (e: Exception) {
                // stats are best-effort
            }
        }
    }

    fun clearMessages() {
        _error.value = null
        _info.value = null
    }

    fun saveProfile(fullName: String, bio: String, phone: String, isPublic: Boolean) {
        val me = repo.myId() ?: return
        if (fullName.trim().length < 2) {
            _error.value = "Enter your full name."
            return
        }
        viewModelScope.launch {
            _saving.value = true
            val updated = try {
                repo.updateProfile(me, fullName.trim(), bio.trim().ifBlank { null }, phone.trim().ifBlank { null }, isPublic)
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
                null
            }
            _saving.value = false
            if (updated != null) {
                SessionManager.setProfile(updated)
                _info.value = "Profile updated."
            }
        }
    }

    /**
     * v3.1.0 extended fields: city, education, status line, social links,
     * theme colour. [themeColor] must be null/blank or "#RRGGBB" — the DB
     * check rejects anything else, so invalid input is refused here.
     * Saved separately from [saveProfile] so the old save path is untouched.
     */
    fun saveExtendedProfile(
        city: String,
        education: String,
        statusLine: String,
        socialLinks: SocialLinks,
        themeColor: String
    ) {
        val me = repo.myId() ?: return
        val color = themeColor.trim().ifBlank { null }
        if (color != null && !Regex("^#[0-9A-Fa-f]{6}$").matches(color)) {
            _error.value = "Theme colour must look like #10B981."
            return
        }
        viewModelScope.launch {
            _saving.value = true
            val updated = try {
                repo.updateProfileExtended(
                    me,
                    city.trim().ifBlank { null },
                    education.trim().ifBlank { null },
                    statusLine.trim().ifBlank { null },
                    socialLinks,
                    color
                )
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
                null
            }
            _saving.value = false
            if (updated != null) {
                SessionManager.setProfile(updated)
                _info.value = "Profile updated."
            }
        }
    }

    /**
     * Upload a new cover photo (v3.1.0). Same 10 MB limit as the avatar;
     * the repository downscales covers to a 1600px longest side.
     * [ext] is png/webp/gif, else jpg; [contentType] is the image MIME type.
     */
    fun uploadCover(bytes: ByteArray, ext: String, contentType: String) {
        val me = repo.myId() ?: return
        if (bytes.size > 10 * 1024 * 1024) {
            _error.value = "Cover photo must be smaller than 10 MB."
            return
        }
        viewModelScope.launch {
            _uploadingCover.value = true
            val url = repo.uploadCover(me, bytes, ext, contentType)
            if (url == null) {
                _uploadingCover.value = false
                _error.value = "Upload failed. Check your connection and try again."
                return@launch
            }
            val updated = repo.setCoverUrl(me, url)
            _uploadingCover.value = false
            if (updated != null) {
                SessionManager.setProfile(updated)
                _info.value = "Cover photo updated."
            } else {
                _error.value = "Cover uploaded but could not save it."
            }
        }
    }

    /**
     * Upload a new profile photo. Photos are downscaled/compressed in
     * the repository before upload; the only client-side rejection left
     * is the 10 MB 'avatars' bucket limit itself.
     * [ext] is png/webp/gif, else jpg; [contentType] is the image MIME type.
     */
    fun uploadAvatar(bytes: ByteArray, ext: String, contentType: String) {
        val me = repo.myId() ?: return
        if (bytes.size > 10 * 1024 * 1024) {
            _error.value = "Photo must be smaller than 10 MB."
            return
        }
        viewModelScope.launch {
            _uploading.value = true
            val url = repo.uploadAvatar(me, bytes, ext, contentType)
            if (url == null) {
                _uploading.value = false
                _error.value = "Upload failed. Check your connection and try again."
                return@launch
            }
            val updated = repo.setAvatarUrl(me, url)
            _uploading.value = false
            if (updated != null) {
                SessionManager.setProfile(updated)
                _info.value = "Profile photo updated."
            } else {
                _error.value = "Photo uploaded but could not save it."
            }
        }
    }

    /**
     * Remove the profile photo (v3.2.0): set avatar_url to NULL in
     * Supabase and clear the local profile, so the photo is really
     * gone (Avatar falls back to initials). The old Storage file is
     * left unreferenced. The screen asks for confirmation first.
     */
    fun removeAvatar() {
        val me = repo.myId() ?: return
        viewModelScope.launch {
            _uploading.value = true
            val updated = try {
                repo.clearAvatarUrl(me)
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
                null
            }
            _uploading.value = false
            if (updated != null) {
                SessionManager.setProfile(updated)
                _info.value = "Profile photo removed."
            } else if (_error.value == null) {
                _error.value = "Could not remove the photo. Check your connection and try again."
            }
        }
    }

    fun changePassword(newPassword: String) {
        if (newPassword.length < 8) {
            _error.value = "Password must be at least 8 characters."
            return
        }
        viewModelScope.launch {
            _saving.value = true
            try {
                authRepo.updatePassword(newPassword)
                _info.value = "Password updated."
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
            _saving.value = false
        }
    }

    /**
     * Notifications ON/OFF (Settings, v3.0.2) — the token layer of the
     * toggle. The preference itself is saved by SettingsScreen (Prefs);
     * this syncs pingchat_profiles.fcm_token with it: OFF clears the token
     * so senders' PushSender calls find no token (every sender does
     * `peer.fcmToken ?: return` / `?.let`), ON re-saves the current FCM
     * token. Best-effort: a failed write never blocks the toggle — the
     * persisted preference + the display gate in PingChatMessagingService
     * still hold. Calls are NOT affected (CallManager, not push).
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val me = repo.myId() ?: return@launch
            try {
                if (enabled) {
                    val token = Supabase.fcmToken() ?: return@launch
                    Supabase.client.from("pingchat_profiles").update({
                        set("fcm_token", token)
                    }) {
                        filter { eq("id", me) }
                    }
                    _info.value = "Notifications turned on."
                } else {
                    Supabase.client.from("pingchat_profiles").update({
                        set("fcm_token", null as String?)
                    }) {
                        filter { eq("id", me) }
                    }
                    _info.value = "Notifications turned off."
                }
            } catch (e: Exception) {
                // best-effort — the toggle + display gate still apply
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.signOut()
            // SessionManager picks up SIGNED_OUT and routes to login.
        }
    }
}
