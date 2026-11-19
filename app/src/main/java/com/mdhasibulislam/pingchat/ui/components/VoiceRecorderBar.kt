package com.mdhasibulislam.pingchat.ui.components

import android.media.MediaRecorder
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Voice recording (v3.1.0, worker D): WhatsApp-style hold-to-record.
 *
 * Why a new engine instead of ChatViewModel's recorder: the ViewModel's
 * recorder is private with no cancel path — a swipe-to-cancel can never
 * stop it, so it would auto-send at 120s after the user cancelled. This
 * [VoiceRecorder] owns the MediaRecorder, so hold / lock / cancel all
 * work. Sending still goes through the same backend call
 * (ChatRepository.sendVoice) from ChatScreen, which then refreshes the
 * list and sends the peer push exactly like the old path did.
 * ChatViewModel.startRecording / stopRecordingAndSend are no longer
 * called from the UI (left in the ViewModel untouched, per scope).
 */
class VoiceRecorder {
    companion object {
        /** Max recording length — same 120s as the old ViewModel path. */
        const val MAX_SECONDS = 120

        /** Live waveform history kept (newest last). */
        const val AMPLITUDE_HISTORY = 40

        /**
         * Minimum recording length. MediaRecorder.stop() throws
         * (RuntimeException: "stop failed") when it is called before the
         * encoder has produced any audio — a tap, or a release that lands
         * within a frame of the press. stopAndGetBytes() waits out the
         * remainder of this window so a short hold still sends a valid
         * ~1s voice note instead of failing with null bytes.
         */
        const val MIN_RECORD_MS = 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var tickerJob: Job? = null
    private var autoStopFired = false
    /** Elapsed-realtime clock value when the current recording started. */
    private var startTimeMs = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _seconds = MutableStateFlow(0)
    val seconds: StateFlow<Int> = _seconds.asStateFlow()

    /** Live mic levels 0..1 (newest last), sampled ~10x/second. */
    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()

    /** True once the user slid up to lock: recording continues hands-free. */
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** True while the finger is slid left past the cancel threshold. */
    private val _cancelArmed = MutableStateFlow(false)
    val cancelArmed: StateFlow<Boolean> = _cancelArmed.asStateFlow()

    /** Called when the 120s limit is hit — the caller sends the voice. */
    var onAutoStop: (() -> Unit)? = null

    /** Start recording into [file]. Returns false when already recording or on failure. */
    @Suppress("DEPRECATION")
    fun start(file: File): Boolean {
        if (_isRecording.value) return false
        var rec: MediaRecorder? = null
        return try {
            rec = MediaRecorder()
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            outputFile = file
            autoStopFired = false
            startTimeMs = SystemClock.elapsedRealtime()
            _seconds.value = 0
            _amplitudes.value = emptyList()
            _locked.value = false
            _cancelArmed.value = false
            _isRecording.value = true
            startTicker()
            true
        } catch (_: Exception) {
            // A half-built MediaRecorder holds the mic until released —
            // without this, one failed start breaks every later attempt.
            try {
                rec?.release()
            } catch (_: Exception) {
            }
            false
        }
    }

    /** Stop and return the recorded bytes (null when too short / failed). Deletes the temp file. */
    suspend fun stopAndGetBytes(): ByteArray? {
        val rec = recorder ?: return null
        recorder = null
        tickerJob?.cancel()
        tickerJob = null
        _isRecording.value = false
        val file = outputFile
        outputFile = null
        _locked.value = false
        _cancelArmed.value = false
        // MediaRecorder.stop() throws when almost no audio was captured
        // yet (a tap, or a release within a frame of the press). Let the
        // recording run until MIN_RECORD_MS so stop() succeeds and the
        // file holds a real, playable .m4a instead of failing outright.
        val elapsed = SystemClock.elapsedRealtime() - startTimeMs
        if (elapsed < MIN_RECORD_MS) delay(MIN_RECORD_MS - elapsed)
        val bytes = try {
            rec.stop()
            rec.release()
            withContext(Dispatchers.IO) { file?.takeIf { it.exists() }?.readBytes() }
        } catch (_: Exception) {
            try {
                rec.release()
            } catch (_: Exception) {
            }
            null
        }
        try {
            file?.delete()
        } catch (_: Exception) {
        }
        _seconds.value = 0
        _amplitudes.value = emptyList()
        return bytes?.takeIf { it.isNotEmpty() }
    }

    /** Stop and discard the recording — nothing is sent. */
    fun cancel() {
        val rec = recorder ?: return
        recorder = null
        tickerJob?.cancel()
        tickerJob = null
        _isRecording.value = false
        val file = outputFile
        outputFile = null
        try {
            rec.stop()
        } catch (_: Exception) {
        }
        try {
            rec.release()
        } catch (_: Exception) {
        }
        try {
            file?.delete()
        } catch (_: Exception) {
        }
        _seconds.value = 0
        _amplitudes.value = emptyList()
        _locked.value = false
        _cancelArmed.value = false
    }

    fun setLocked(locked: Boolean) {
        if (_isRecording.value) _locked.value = locked
    }

    fun setCancelArmed(armed: Boolean) {
        if (_isRecording.value) _cancelArmed.value = armed
    }

    /** Release everything (call from onDispose). Discards an in-progress recording. */
    fun release() {
        if (_isRecording.value) cancel()
        tickerJob?.cancel()
        scope.cancel()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var elapsedMs = 0L
            while (true) {
                delay(100)
                val rec = recorder ?: break
                val level = try {
                    rec.maxAmplitude / 32767f
                } catch (_: Exception) {
                    0f
                }
                _amplitudes.update { (it + level.coerceIn(0f, 1f)).takeLast(AMPLITUDE_HISTORY) }
                elapsedMs += 100
                if (elapsedMs >= 1000) {
                    elapsedMs = 0
                    val next = _seconds.value + 1
                    _seconds.value = next
                    if (next >= MAX_SECONDS) {
                        if (!autoStopFired) {
                            autoStopFired = true
                            onAutoStop?.invoke()
                        }
                        break
                    }
                }
            }
        }
    }
}

/**
 * Recording bar shown in the composer while recording: red dot, live
 * timer, live waveform from the real mic level, and trash (cancel) +
 * send buttons. The buttons are shown for the whole recording, not
 * only once locked: a recording can start without a live hold gesture
 * (the mic-permission dialog grants mid-press, and the mic button
 * leaves composition when recording starts), so the bar's buttons are
 * the only finish path in those cases — without them the recording
 * runs until the 120s auto-send or the chat is left. While held (not
 * locked) the hint explains slide-left to cancel / slide-up to lock.
 */
@Composable
fun VoiceRecorderBar(
    recorder: VoiceRecorder,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = pingChatColors
    val seconds by recorder.seconds.collectAsState()
    val amplitudes by recorder.amplitudes.collectAsState()
    val locked by recorder.locked.collectAsState()
    val cancelArmed by recorder.cancelArmed.collectAsState()
    val minutes = seconds / 60
    val secs = (seconds % 60).toString().padStart(2, '0')

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(colors.inputBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$minutes:$secs",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text
            )
            Spacer(Modifier.width(8.dp))
            if (cancelArmed) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Release to cancel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Red,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Live waveform from the real mic level.
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                ) {
                    val count = VoiceRecorder.AMPLITUDE_HISTORY
                    val barWidth = 3.dp.toPx()
                    val gap = 2.dp.toPx()
                    val step = barWidth + gap
                    val totalWidth = count * step - gap
                    var x = (size.width - totalWidth) / 2f
                    val samples = amplitudes.takeLast(count)
                    for (i in 0 until count) {
                        val level = samples.getOrNull(i) ?: 0.05f
                        val barHeight = ((0.15f + level * 0.85f) * size.height)
                            .coerceAtLeast(4.dp.toPx())
                        drawRoundRect(
                            color = colors.text.copy(alpha = 0.75f),
                            topLeft = Offset(x, (size.height - barHeight) / 2f),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                        x += step
                    }
                }
                // Trash (cancel) + send are always visible while
                // recording — see the KDoc above: when the hold gesture
                // is gone (button unmounted, or the recording started
                // from the permission dialog), these are the only way
                // to finish. When locked they are the same buttons.
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Cancel recording",
                        tint = Color.Red
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Emerald)
                        .clickable { onSend() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send voice message",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
            }
        }
        if (!locked) {
            Text(
                text = "Slide left to cancel  •  Slide up to lock",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = colors.muted,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
            )
        }
    }
}

/**
 * Mic button with hold-to-record gestures. It must stay mounted while
 * recording (ChatScreen keeps it outside the recording/text-field
 * switch) so the hold gesture is never cut off by recomposition.
 *
 * - Finger down: [onPressStart] (returns true when recording started).
 * - Slide up past the lock threshold: [onLock] (hands-free recording).
 * - Slide left past the cancel threshold: arms cancel; releasing then
 *   calls [onCancel], releasing otherwise calls [onReleaseSend].
 * - While locked, releasing does nothing — the bar's send/trash buttons
 *   take over.
 * - [blocked] (voice not allowed for this peer): shows a muted mic-off
 *   icon; a tap calls [onBlockedTap] instead of recording.
 */
@Composable
fun VoiceMicButton(
    blocked: Boolean,
    recording: Boolean,
    locked: Boolean,
    onPressStart: () -> Boolean,
    onLock: () -> Unit,
    onCancelArmed: (Boolean) -> Unit,
    onReleaseSend: () -> Unit,
    onCancel: () -> Unit,
    onBlockedTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = pingChatColors
    val density = LocalDensity.current
    val lockThresholdPx = with(density) { 56.dp.toPx() }
    val cancelThresholdPx = with(density) { 72.dp.toPx() }
    val currentOnPressStart by rememberUpdatedState(onPressStart)
    val currentOnLock by rememberUpdatedState(onLock)
    val currentOnCancelArmed by rememberUpdatedState(onCancelArmed)
    val currentOnReleaseSend by rememberUpdatedState(onReleaseSend)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val currentOnBlockedTap by rememberUpdatedState(onBlockedTap)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                when {
                    blocked -> Color.Transparent
                    recording -> Color.Red.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
            )
            .pointerInput(blocked, lockThresholdPx, cancelThresholdPx) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        down.consume()
                        if (blocked) {
                            currentOnBlockedTap()
                            // Wait for the finger to lift before accepting
                            // the next tap.
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed && change.previousPressed) break
                            }
                            continue
                        }
                        var started = currentOnPressStart()
                        var isLocked = false
                        var cancelArmed = false
                        var dx = 0f
                        var dy = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed && change.previousPressed) {
                                change.consume()
                                break
                            }
                            val delta = change.position - change.previousPosition
                            dx += delta.x
                            dy += delta.y
                            change.consume()
                            if (started && !isLocked && dy < -lockThresholdPx) {
                                isLocked = true
                                currentOnLock()
                            }
                            if (started) {
                                val armed = dx < -cancelThresholdPx
                                if (armed != cancelArmed) {
                                    cancelArmed = armed
                                    currentOnCancelArmed(armed)
                                }
                            }
                        }
                        if (!started) continue
                        if (cancelArmed) {
                            currentOnCancelArmed(false)
                            currentOnCancel()
                        } else if (!isLocked) {
                            currentOnReleaseSend()
                        }
                        // Locked: keep recording; the bar's buttons finish it.
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            when {
                blocked -> Icons.Default.MicOff
                locked -> Icons.Default.Lock
                else -> Icons.Default.Mic
            },
            contentDescription = when {
                blocked -> "Voice messages not allowed"
                recording -> "Recording voice message"
                else -> "Record a voice message"
            },
            tint = when {
                blocked -> colors.muted.copy(alpha = 0.5f)
                recording -> Color.Red
                else -> colors.text
            }
        )
    }
}
