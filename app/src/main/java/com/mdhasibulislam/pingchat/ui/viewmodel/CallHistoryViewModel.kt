package com.mdhasibulislam.pingchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.CallHistoryItem
import com.mdhasibulislam.pingchat.data.CallRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Calls tab: my per-user call history, newest first. Each row carries the
 * peer's profile (avatar + current name); deleting a row removes it for
 * me only — the other side keeps their own history row.
 */
class CallHistoryViewModel : ViewModel() {
    private val repo = CallRepository()

    private val _history = MutableStateFlow<List<CallHistoryItem>>(emptyList())
    val history: StateFlow<List<CallHistoryItem>> = _history.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val rows = repo.loadCallHistoryRows()
            val profiles = repo.loadProfilesByIds(rows.map { it.peerId })
            _history.value = rows.map { CallHistoryItem(it, profiles[it.peerId]) }
            _loading.value = false
        }
    }

    /** Delete one history entry for me only. */
    fun delete(item: CallHistoryItem) {
        viewModelScope.launch {
            try {
                repo.deleteCallHistory(item.row.id)
                _history.value = _history.value.filter { it.row.id != item.row.id }
            } catch (e: Exception) {
                // keep the row visible; the delete simply did not happen
            }
        }
    }
}
