package com.mdhasibulislam.pingchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.ChatRepository
import com.mdhasibulislam.pingchat.data.ConversationItem
import com.mdhasibulislam.pingchat.data.FriendsRepository
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.data.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Chats list. Refreshes on every realtime message event (debounced).
 *  v3.1.0: pinned chats sort first (ChatRepository), archived chats are
 *  split into [archivedItems], and pin/mute/archive actions live here. */
class ChatsViewModel : ViewModel() {
    private val repo = ChatRepository()

    private val _items = MutableStateFlow<List<ConversationItem>>(emptyList())
    val items: StateFlow<List<ConversationItem>> = _items.asStateFlow()

    /** Archived chats (hidden from the main list; new messages do NOT
     *  unarchive them — only the user does, from the Archived section). */
    private val _archivedItems = MutableStateFlow<List<ConversationItem>>(emptyList())
    val archivedItems: StateFlow<List<ConversationItem>> = _archivedItems.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Set by the screen (MainScreen passes Prefs) so mute changes can
     *  update the on-device muted-ids cache PingChatMessagingService
     *  reads. Without it, mute still persists server-side but push
     *  suppression only starts after the next refresh. */
    private var prefs: Prefs? = null

    fun attachPrefs(p: Prefs) {
        prefs = p
    }

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
                val all = repo.loadConversations()
                // Archived chats live in their own list; the main list
                // keeps pinned-first order from the repository.
                _items.value = all.filter { !it.isArchived }
                _archivedItems.value = all.filter { it.isArchived }
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

    // ---------- v3.1.0: pin / mute / archive ----------
    // Each action updates the row locally at once (no waiting for the
    // next feed tick) and refreshes afterwards so the server state wins.

    /** Pin a chat to the top of the list, or unpin it. */
    fun setPinned(item: ConversationItem, pinned: Boolean) {
        viewModelScope.launch {
            _items.update { list ->
                list.map {
                    if (it.conversation.id == item.conversation.id) it.copy(isPinned = pinned)
                    else it
                }
            }
            if (!repo.setChatPinned(item.conversation.id, pinned)) {
                _error.value = "Could not ${if (pinned) "pin" else "unpin"} this chat. Try again."
            }
            refresh()
        }
    }

    /** Mute a chat (no push notifications) or unmute it. Also updates
     *  the on-device muted-ids cache PingChatMessagingService reads. */
    fun setMuted(item: ConversationItem, muted: Boolean) {
        viewModelScope.launch {
            _items.update { list ->
                list.map {
                    if (it.conversation.id == item.conversation.id) it.copy(isMuted = muted)
                    else it
                }
            }
            if (repo.setChatMuted(item.conversation.id, muted)) {
                prefs?.setConversationMuted(item.conversation.id, muted)
            } else {
                _error.value = "Could not ${if (muted) "mute" else "unmute"} this chat. Try again."
            }
            refresh()
        }
    }

    /** Archive a chat (moves it to the Archived section) or unarchive it.
     *  Archiving never clears the unread badge; unarchiving returns the
     *  chat to the main list in its pinned-first position. */
    fun setArchived(item: ConversationItem, archived: Boolean) {
        viewModelScope.launch {
            if (archived) {
                _items.update { list -> list.filterNot { it.conversation.id == item.conversation.id } }
                _archivedItems.update { list -> list + item.copy(isArchived = true) }
            } else {
                _archivedItems.update { list -> list.filterNot { it.conversation.id == item.conversation.id } }
            }
            if (!repo.setChatArchived(item.conversation.id, archived)) {
                _error.value = "Could not ${if (archived) "archive" else "unarchive"} this chat. Try again."
            }
            refresh()
        }
    }
}
