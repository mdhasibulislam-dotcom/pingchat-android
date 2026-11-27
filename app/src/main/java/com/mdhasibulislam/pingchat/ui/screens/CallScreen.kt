package com.mdhasibulislam.pingchat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SwitchCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.mdhasibulislam.pingchat.data.CallManager
import com.mdhasibulislam.pingchat.data.CallSession
import com.mdhasibulislam.pingchat.data.CallStatus
import com.mdhasibulislam.pingchat.data.IncomingCall
import com.mdhasibulislam.pingchat.data.Profile
import com.mdhasibulislam.pingchat.ui.components.Avatar
import com.mdhasibulislam.pingchat.ui.components.AvatarSize
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.CallViewModel
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/** "mm:ss" call timer. */
private fun formatCallDuration(seconds: Int): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun callStatusText(session: CallSession): String =
    if (session.startedAtMillis != null) formatCallDuration(session.durationSec)
    else if (session.status == CallStatus.CALLING) "Calling…"
    else "Connecting…"

/** Incoming call: peer avatar + name, Accept audio / Accept video / Decline. */
@Composable
fun IncomingCallDialog(incoming: IncomingCall, viewModel: CallViewModel) {
    val colors = pingChatColors
    val context = LocalContext.current
    val peer = Profile(id = incoming.peerId, username = "", fullName = incoming.peerName)
    // v3.2.5: the accept buttons used to call acceptCall with no
    // permission check — if CAMERA was never granted (e.g. only audio
    // calls were ever made), CallManager.createLocalTracks threw a
    // SecurityException, the call was declined to the caller and the
    // callee only saw "Microphone/camera unavailable". Gate on the same
    // permissions as the outgoing path (ChatScreen.requestCall /
    // MainScreen.requestCall): audio needs the microphone, video needs
    // the microphone + camera.
    var pendingAcceptKind by remember { mutableStateOf<String?>(null) }
    val acceptPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val kind = pendingAcceptKind
        pendingAcceptKind = null
        if (kind == null) return@rememberLauncherForActivityResult
        val needed = if (kind == "video") {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.all { result[it] == true }) {
            viewModel.acceptCall(kind)
        } else {
            Toast.makeText(
                context,
                if (kind == "video") "Microphone and camera permission is needed for video calls."
                else "Microphone permission is needed for audio calls.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    fun requestAccept(kind: String) {
        val needed = if (kind == "video") {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            viewModel.acceptCall(kind)
        } else {
            pendingAcceptKind = kind
            acceptPermission.launch(needed)
        }
    }
    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(colors.card)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(profile = peer, size = AvatarSize.LG, online = false)
            Spacer(Modifier.height(12.dp))
            Text(
                text = incoming.peerName.ifBlank { "Unknown caller" },
                style = MaterialTheme.typography.titleLarge,
                color = colors.text
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Incoming ${if (incoming.kind == "video") "video" else "audio"} call",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.declineCall() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.weight(1f)
                ) { Text("Decline") }
                Button(
                    onClick = { requestAccept("audio") },
                    modifier = Modifier.weight(1f)
                ) { Text("Audio") }
                Button(
                    onClick = { requestAccept("video") },
                    modifier = Modifier.weight(1f)
                ) { Text("Video") }
            }
        }
    }
}

/** Full-screen in-call UI, rendered above everything from MainScreen. */
@Composable
fun ActiveCallScreen(session: CallSession, viewModel: CallViewModel) {
    val localTrack by viewModel.localVideoTrack.collectAsState()
    val remoteTrack by viewModel.remoteVideoTrack.collectAsState()
    val isVideo = session.kind == "video"
    val peerName = session.peer.fullName.ifBlank { session.peer.username }.ifBlank { "Unknown" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isVideo && session.hasRemoteVideo && remoteTrack != null) {
            // Remote video fills the screen; audio comes from the same
            // WebRTC stream (no separate audio element needed on Android).
            VideoRenderer(
                track = remoteTrack,
                mirror = false,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Audio call, or the remote video has not arrived yet:
            // big avatar + name + timer, Messenger-style.
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Avatar(profile = session.peer, size = AvatarSize.LG, online = false)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = callStatusText(session),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Top bar: name + status/timer (over the video).
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = callStatusText(session),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // My camera preview, small, top-right (video calls only).
        if (isVideo && localTrack != null) {
            VideoRenderer(
                track = localTrack,
                mirror = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 84.dp, end = 16.dp)
                    .size(width = 96.dp, height = 128.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        // Controls: mute, speaker, camera/flip (video only), end call.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 28.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallControlButton(
                icon = if (session.muted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (session.muted) "Unmute" else "Mute",
                active = !session.muted,
                onClick = { viewModel.toggleMute() }
            )
            CallControlButton(
                icon = if (session.speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                label = "Speaker",
                active = session.speakerOn,
                onClick = { viewModel.toggleSpeaker() }
            )
            if (isVideo) {
                CallControlButton(
                    icon = if (session.cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    label = "Camera",
                    active = session.cameraEnabled,
                    onClick = { viewModel.toggleCamera() }
                )
                CallControlButton(
                    icon = Icons.Default.SwitchCamera,
                    label = "Flip",
                    active = true,
                    onClick = { viewModel.switchCamera() }
                )
            }
            IconButton(
                onClick = { viewModel.endCall() },
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
            ) {
                Icon(
                    Icons.Default.CallEnd,
                    contentDescription = "End call",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    if (active) Color.White.copy(alpha = 0.2f)
                    else Color.White.copy(alpha = 0.08f)
                )
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

/**
 * A WebRTC video track rendered into a SurfaceViewRenderer.
 *
 * v3.2.5: the sink used to be attached only in a DisposableEffect on
 * the track, while the view was created in the AndroidView factory —
 * if the factory ran while eglContext was null (or the view was
 * recreated), the sink never got attached and the video stayed black
 * with no error. The sink is now attached in the factory AND
 * re-attached whenever the track changes, and init() is never called
 * with a null EGL context.
 */
@Composable
private fun VideoRenderer(track: VideoTrack?, mirror: Boolean, modifier: Modifier) {
    val eglContext = CallManager.eglContext
    val attachedTrack = remember { mutableStateOf<VideoTrack?>(null) }
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                if (eglContext != null) {
                    init(eglContext, null)
                    setEnableHardwareScaler(true)
                    setMirror(mirror)
                    track?.let {
                        try {
                            it.addSink(this)
                            attachedTrack.value = it
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        },
        update = { view ->
            if (attachedTrack.value !== track) {
                attachedTrack.value?.let {
                    try {
                        it.removeSink(view)
                    } catch (e: Exception) {
                    }
                }
                if (track != null) {
                    try {
                        track.addSink(view)
                        attachedTrack.value = track
                    } catch (e: Exception) {
                    }
                } else {
                    attachedTrack.value = null
                }
            }
        },
        modifier = modifier,
        onRelease = { view ->
            attachedTrack.value?.let {
                try {
                    it.removeSink(view)
                } catch (e: Exception) {
                }
            }
            attachedTrack.value = null
            view.release()
        }
    )
}
