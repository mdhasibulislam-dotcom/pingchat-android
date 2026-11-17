package com.mdhasibulislam.pingchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.CallManager
import com.mdhasibulislam.pingchat.data.Profile
import kotlinx.coroutines.launch

/**
 * Thin facade over the app-wide CallManager (one call at a time): the
 * screens collect its StateFlows and call these functions. All state
 * lives in CallManager so the incoming-call dialog and the in-call
 * screen work from any tab.
 */
class CallViewModel : ViewModel() {
    val incoming = CallManager.incoming
    val session = CallManager.session
    val localVideoTrack = CallManager.localVideoTrack
    val remoteVideoTrack = CallManager.remoteVideoTrack
    val error = CallManager.error

    fun clearError() = CallManager.clearError()

    /** Start an audio/video call. Returns an error message, or null. */
    suspend fun startCall(peer: Profile, kind: String, conversationId: String): String? =
        CallManager.startCall(peer, kind, conversationId)

    /** Accept the ringing call as audio or video. Errors go to the error flow. */
    fun acceptCall(kind: String) {
        viewModelScope.launch {
            val err = CallManager.acceptCall(kind)
            if (err != null) CallManager.postError(err)
        }
    }

    fun declineCall() {
        viewModelScope.launch { CallManager.declineCall() }
    }

    fun endCall() {
        viewModelScope.launch { CallManager.endCall() }
    }

    fun toggleMute() = CallManager.toggleMute()
    fun toggleSpeaker() = CallManager.toggleSpeaker()
    fun toggleCamera() = CallManager.toggleCamera()
    fun switchCamera() = CallManager.switchCamera()
}
