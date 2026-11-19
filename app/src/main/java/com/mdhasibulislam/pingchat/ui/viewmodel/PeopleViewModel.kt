package com.mdhasibulislam.pingchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.CallManager
import com.mdhasibulislam.pingchat.data.ChatRepository
import com.mdhasibulislam.pingchat.data.FriendState
import com.mdhasibulislam.pingchat.data.FriendsRepository
import com.mdhasibulislam.pingchat.data.Friendship
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.data.PrivacyRepository
import com.mdhasibulislam.pingchat.data.ProfileRepository
import com.mdhasibulislam.pingchat.data.PushSender
import com.mdhasibulislam.pingchat.data.SessionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** People tab: directory, friend requests, friends. Mirrors js/people.js + js/friends.js. */
class PeopleViewModel : ViewModel() {
    private val repo = FriendsRepository()
    private val profileRepo = ProfileRepository()
    private val chatRepo = ChatRepository()
    private val privacyRepo = PrivacyRepository()

    val myId: String? get() = repo.myId()

    private val _users = MutableStateFlow<List<Profile>>(emptyList())
    val users: StateFlow<List<Profile>> = _users.asStateFlow()

    private val _requests = MutableStateFlow<List<Pair<Friendship, Profile>>>(emptyList())
    val requests: StateFlow<List<Pair<Friendship, Profile>>> = _requests.asStateFlow()

    private val _friends = MutableStateFlow<List<Profile>>(emptyList())
    val friends: StateFlow<List<Profile>> = _friends.asStateFlow()

    private val _statuses = MutableStateFlow<Map<String, FriendState>>(emptyMap())
    val statuses: StateFlow<Map<String, FriendState>> = _statuses.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** A conversation id to open after "Message" is tapped (consumed by the screen). */
    private val _openedConversation = MutableStateFlow<String?>(null)
    val openedConversation: StateFlow<String?> = _openedConversation.asStateFlow()

    // ---------- v3.1.0 public profile ----------

    /** Mutual friends with the profile currently being viewed. */
    private val _mutualFriends = MutableStateFlow<List<Profile>>(emptyList())
    val mutualFriends: StateFlow<List<Profile>> = _mutualFriends.asStateFlow()

    /** How many distinct users viewed the profile being viewed. */
    private val _profileViewCount = MutableStateFlow(0L)
    val profileViewCount: StateFlow<Long> = _profileViewCount.asStateFlow()

    /**
     * What the viewer may see on someone's public profile.
     * Sync fallback (old rule: public profile, or friend / pending request
     * on a private one), same as PeopleScreen.canSeeDetails.
     */
    fun visibilityFor(profile: Profile, state: FriendState): Pair<Boolean, Boolean> {
        val canSee = profile.isPublic ||
            state == FriendState.FRIENDS ||
            state == FriendState.PENDING_IN ||
            state == FriendState.PENDING_OUT
        return canSee to canSee
    }

    /**
     * Privacy-checked visibility for a public profile (v3.1.0, worker A):
     * (canSeeBio, canSeeLastSeen) from PrivacyRepository. Starts with the
     * [visibilityFor] fallback, then replaces it when the check returns.
     * Null until [loadVisibilityFor] is called.
     */
    private val _visibility = MutableStateFlow<Pair<Boolean, Boolean>?>(null)
    val visibility: StateFlow<Pair<Boolean, Boolean>?> = _visibility.asStateFlow()

    fun loadVisibilityFor(profile: Profile, state: FriendState) {
        _visibility.value = visibilityFor(profile, state)
        viewModelScope.launch {
            try {
                val bio = privacyRepo.canSeeBioOf(profile.id)
                val lastSeen = privacyRepo.canSeeLastSeen(profile.id)
                _visibility.value = bio to lastSeen
            } catch (_: Exception) {
                // Keep the fallback — privacy check is best-effort.
            }
        }
    }

    /** Load mutual friends + visitor count for a public profile. */
    fun loadPublicProfile(userId: String) {
        viewModelScope.launch {
            _mutualFriends.value = try {
                profileRepo.loadMutualFriends(userId)
            } catch (e: Exception) {
                emptyList()
            }
            _profileViewCount.value = try {
                profileRepo.profileViewCount(userId)
            } catch (e: Exception) {
                0L
            }
        }
    }

    /**
     * Record that I viewed this profile (ignored for my own profile,
     * server-side) and refresh the visitor count. Best-effort.
     */
    fun recordProfileView(userId: String) {
        viewModelScope.launch {
            try {
                profileRepo.recordProfileView(userId)
                _profileViewCount.value = profileRepo.profileViewCount(userId)
            } catch (e: Exception) {
                // views are best-effort
            }
        }
    }

    /**
     * Start an audio/video call to a friend from their public profile.
     * Same path as ChatScreen: get-or-create the 1-to-1 conversation,
     * then CallManager.startCall. The caller (screen) checks the mic /
     * camera permission first. Errors go to [_error]; returns true when
     * the call started. Only friends can be called — the RPC refuses
     * non-friends, and the screen only shows the buttons to friends.
     */
    fun startCall(userId: String, kind: String) {
        viewModelScope.launch {
            try {
                val peer = _users.value.firstOrNull { it.id == userId }
                    ?: _friends.value.firstOrNull { it.id == userId }
                    ?: profileRepo.getProfile(userId)
                    ?: run { _error.value = "Could not load this profile."; return@launch }
                val conversationId = chatRepo.getOrCreateConversation(userId)
                val err = CallManager.startCall(peer, kind, conversationId)
                if (err != null) _error.value = err
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
        }
    }

    init {
        refresh()
        viewModelScope.launch {
            try {
                repo.friendshipsFlow().collect {
                    delay(300)
                    refresh()
                }
            } catch (e: Exception) {
                // live badge is best-effort
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val me = repo.myId()
                val all = repo.loadAllUsers().filter { it.id != me }
                _users.value = all
                _requests.value = repo.loadIncomingRequests()
                _friends.value = repo.loadFriends()
                _error.value = null
                // Friendship state per person (parallel queries).
                val statuses = all.map { user ->
                    async { user.id to repo.friendshipStatus(user.id) }
                }.awaitAll().toMap()
                _statuses.value = statuses
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
            _loading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun sendRequest(userId: String) {
        viewModelScope.launch {
            try {
                repo.sendFriendRequest(userId)
                _statuses.value = _statuses.value + (userId to FriendState.PENDING_OUT)
                notifyRecipient(userId)
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
        }
    }

    /**
     * Friend-request push (v3.0.2): tell the recipient a request arrived.
     * Best-effort only — a failed push must never fail the request itself,
     * same as the chat-message push in ChatViewModel.notifyPeer and the
     * call push in CallManager. The recipient's fcm_token comes from their
     * profile (directory cache first, fresh load as fallback);
     * PushSender.sendPush swallows its own errors and returns false.
     */
    private fun notifyRecipient(userId: String) {
        viewModelScope.launch {
            val recipient = _users.value.firstOrNull { it.id == userId }
                ?: repo.loadProfile(userId)
                ?: return@launch
            val token = recipient.fcmToken ?: return@launch
            val myName = SessionManager.profile.value?.fullName?.ifBlank {
                SessionManager.profile.value?.username
            } ?: "PingChat"
            val myId = repo.myId() ?: return@launch
            val myAvatar = SessionManager.profile.value?.avatarUrl ?: ""
            PushSender.sendPush(
                token,
                "$myName sent you a friend request",
                "Tap to view and respond",
                mapOf(
                    "type" to "friend_request",
                    "senderId" to myId,
                    "senderName" to myName,
                    "senderAvatar" to myAvatar
                )
            )
        }
    }

    fun cancelRequest(userId: String) {
        viewModelScope.launch {
            try {
                repo.cancelFriendRequest(userId)
                _statuses.value = _statuses.value + (userId to FriendState.NONE)
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
        }
    }

    fun respond(requesterId: String, accept: Boolean) {
        viewModelScope.launch {
            try {
                repo.respondFriendRequest(requesterId, accept)
                if (accept) {
                    // v3.0.2: auto-create the 1-to-1 conversation so the new
                    // friend appears in the Chats list right away (both
                    // sides — the RPC inserts both participant rows, so the
                    // requester's list picks it up too). Best-effort: a
                    // failure here must NEVER fail the accept itself.
                    try {
                        chatRepo.getOrCreateConversation(requesterId)
                    } catch (e: Exception) {
                        // Accept already succeeded; the chat appears on
                        // first message instead (openChat creates it).
                    }
                }
                refresh()
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
        }
    }

    /** Open (or create) the 1-to-1 chat with a friend. */
    fun openChat(userId: String) {
        viewModelScope.launch {
            try {
                val conversationId = chatRepo.getOrCreateConversation(userId)
                _openedConversation.value = conversationId
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
        }
    }

    fun consumeOpenedConversation() {
        _openedConversation.value = null
    }
}
