package com.mdhasibulislam.pingchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.ChatRepository
import com.mdhasibulislam.pingchat.data.ConversationItem
import com.mdhasibulislam.pingchat.data.SessionManager
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
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _items.value = repo.loadConversations()
                _error.value = null
            } catch (e: Exception) {
                _error.value = AuthRepository.friendlyError(e)
            }
            _loading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
