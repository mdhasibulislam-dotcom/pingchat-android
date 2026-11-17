package com.mdhasibulislam.pingchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.ChatRepository
import com.mdhasibulislam.pingchat.data.FriendsRepository
import com.mdhasibulislam.pingchat.data.ProfileRepository
import com.mdhasibulislam.pingchat.data.SessionManager
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
     * Upload a new profile photo. The web app accepts images up to 2 MB.
     * [ext] is png/webp/gif, else jpg; [contentType] is the image MIME type.
     */
    fun uploadAvatar(bytes: ByteArray, ext: String, contentType: String) {
        val me = repo.myId() ?: return
        if (bytes.size > 2 * 1024 * 1024) {
            _error.value = "Photo must be smaller than 2 MB."
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

    fun logout() {
        viewModelScope.launch {
            authRepo.signOut()
            // SessionManager picks up SIGNED_OUT and routes to login.
        }
    }
}
