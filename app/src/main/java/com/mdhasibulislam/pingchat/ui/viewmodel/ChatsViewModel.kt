package com.mdhasibulislam.pingchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.ChatRepository
import com.mdhasibulislam.pingchat.data.ConversationItem
import com.mdhasibulislam.pingchat.data.FriendsRepository
import com.mdhasibulislam.pingchat.data.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Chats list. Refreshes on every realtime message event (debounced). */
class ChatsViewModel : ViewModel() {
    private val repo = ChatRepository()

    private val _items = MutableStateFlow<List<ConversationItem>>(emptyList())
    val items: StateFlow<List<ConversationItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            SessionManager.feedTick.collect {
                delay(300) // coalesce bursts of message events
                refresh()
            }
        }
        // v3.0.2 (item 11): my own mark-read writes last_read_at on
        // pingchat_conversation_participants, which fires NO messages-feed
        // tick — so after reading, the list kept the stale unread count
        // until the next message event or tab re-entry (and the
        // feed-tick refresh could run before the mark-read UPDATE landed
        // and cache the stale count). Re-read when a mark-read lands.
        viewModelScope.launch {
            SessionManager.readTick.collect {
                delay(300) // let the mark-read UPDATE commit first
                refresh()
            }
        }
        // v3.0.2: a newly accepted friend's conversation has NO messages,
        // so no feedTick ever fires for it — refresh when friendships
        // change too, so the requester sees the new chat while already
        // sitting on the Chats tab. (Same pattern as PeopleViewModel;
        // multiple postgres-change bindings on one channel are already
        // used by SessionManager's feed, and this is best-effort.)
        viewModelScope.launch {
            try {
                FriendsRepository().friendshipsFlow().collect {
                    delay(300)
                    refresh()
                }
            } catch (e: Exception) {
                // live refresh is best-effort
            }
        }
    }

    // One load at a time: without this, overlapping refreshes (feed tick
    // + read tick + tab re-entry) could finish out of order and a stale
    // load could overwrite a fresh one, putting the unread badge back.
    private var refreshJob: Job? = null

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _loading.value = true
            try {
                _items.value = repo.loadConversations()
                _error.value = null
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
