package com.mdhasibulislam.pingchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.ChatRepository
import com.mdhasibulislam.pingchat.data.FriendState
import com.mdhasibulislam.pingchat.data.FriendsRepository
import com.mdhasibulislam.pingchat.data.Friendship
import com.mdhasibulislam.pingchat.data.Profile
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
    private val chatRepo = ChatRepository()

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

    init {
        refresh()
        viewModelScope.launch {
            val channel = repo.friendshipsFeedChannel()
            launch {
                try {
                    repo.friendshipsChangeFlow(channel).collect {
                        delay(300)
                        refresh()
                    }
                } catch (e: Exception) {
                    // live badge is best-effort
                }
            }
            try {
                channel.subscribe()
            } catch (e: Exception) {
                // offline
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
                // Friendship state per person (parallel RPCs).
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
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
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
