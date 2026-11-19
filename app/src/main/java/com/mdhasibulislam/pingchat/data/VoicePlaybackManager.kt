package com.mdhasibulislam.pingchat.data

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Voice playback (v3.1.0, worker D): ONE global MediaPlayer for the whole
 * app, so a voice message keeps playing when the user leaves the chat
 * (background playback inside the app) and the mini player can pause /
 * stop it from any screen.
 *
 * The old ChatViewModel player was per-chat: leaving the chat cleared the
 * ViewModel and killed playback, and it showed no progress at all. This
 * manager replaces it — ChatScreen no longer calls viewModel.playVoice().
 * Full-listen consume is GONE in v3.1.0 (worker C: the 7-day server rule
 * replaces view-once), so on completion nothing is deleted here.
 *
 * Speeds cycle 1x -> 1.5x -> 2x -> 1x (MediaPlayer.setPlaybackParams,
 * API 23+, minSdk is 24).
 */
object VoicePlaybackManager {

    /** Speeds offered by the speed button, in order. */
    val SPEEDS = listOf(1f, 1.5f, 2f)

    data class PlaybackState(
        val messageId: String? = null,
        val url: String? = null,
        /** Conversation the playing message belongs to — so the mini player
         *  on other screens can jump back to that chat. */
        val conversationId: String? = null,
        val isPlaying: Boolean = false,
        val isLoading: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
        val speed: Float = 1f,
        val error: String? = null
    )

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /**
     * Probed voice durations in seconds, by message id. Filled lazily by
     * [ensureDuration] (MediaMetadataRetriever) so a bubble can show its
     * total time before it is ever played.
     */
    private val _durations = MutableStateFlow<Map<String, Int>>(emptyMap())
    val durations: StateFlow<Map<String, Int>> = _durations.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var tickerJob: Job? = null
    private val probing = mutableSetOf<String>()

    /** "m:ss" — 0:07, 1:32. */
    fun formatTime(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
    }

    fun formatSpeed(speed: Float): String = when (speed) {
        1f -> "1"
        1.5f -> "1.5"
        2f -> "2"
        else -> speed.toString()
    }

    /** Play [messageId]; tapping the same message again pauses / resumes. */
    fun toggle(messageId: String, url: String, conversationId: String? = null) {
        val current = _state.value
        if (current.messageId == messageId && current.url == url) {
            if (current.isPlaying) pause() else resume()
            return
        }
        play(messageId, url, conversationId)
    }

    fun play(messageId: String, url: String, conversationId: String? = null) {
        stopInternal()
        _state.value = PlaybackState(messageId = messageId, url = url, conversationId = conversationId, isLoading = true)
        try {
            val mp = MediaPlayer()
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                _state.update {
                    it.copy(
                        durationMs = try {
                            mp.duration
                        } catch (_: Exception) {
                            0
                        },
                        isLoading = false,
                        isPlaying = true
                    )
                }
                _durations.update { durations ->
                    val seconds = try {
                        mp.duration / 1000
                    } catch (_: Exception) {
                        0
                    }
                    if (seconds > 0) durations + (messageId to seconds) else durations
                }
                applySpeed(mp, _state.value.speed)
                mp.start()
                startTicker()
            }
            mp.setOnCompletionListener {
                stopInternal()
                // Nothing is consumed on a full listen in v3.1.0 — the
                // message stays in the chat until the 7-day server rule.
                _state.value = PlaybackState()
            }
            mp.setOnErrorListener { _, _, _ ->
                stopInternal()
                _state.value = PlaybackState(error = "Could not play voice message.")
                true
            }
            player = mp
            mp.prepareAsync()
        } catch (_: Exception) {
            stopInternal()
            _state.value = PlaybackState(error = "Could not play voice message.")
        }
    }

    fun pause() {
        val mp = player ?: return
        try {
            if (mp.isPlaying) mp.pause()
        } catch (_: Exception) {
        }
        tickerJob?.cancel()
        tickerJob = null
        _state.update { it.copy(isPlaying = false) }
    }

    fun resume() {
        val mp = player ?: return
        try {
            mp.start()
        } catch (_: Exception) {
            return
        }
        applySpeed(mp, _state.value.speed)
        _state.update { it.copy(isPlaying = true) }
        startTicker()
    }

    /** Stop playback entirely (mini player close button). */
    fun stop() {
        stopInternal()
        _state.value = PlaybackState()
    }

    /** Seek within the currently playing message (0..durationMs). */
    fun seekTo(positionMs: Int) {
        val current = _state.value
        val mp = player ?: return
        if (current.durationMs <= 0) return
        val target = positionMs.coerceIn(0, current.durationMs)
        try {
            mp.seekTo(target)
        } catch (_: Exception) {
            return
        }
        _state.update { it.copy(positionMs = target) }
    }

    /** Cycle the speed 1x -> 1.5x -> 2x -> 1x. Applies live while playing. */
    fun cycleSpeed() {
        val current = _state.value
        val nextIndex = (SPEEDS.indexOf(current.speed) + 1) % SPEEDS.size
        val next = SPEEDS[nextIndex]
        _state.update { it.copy(speed = next) }
        player?.let { applySpeed(it, next) }
    }

    /**
     * Probe a voice file's duration without playing it, so the bubble can
     * show its total time up front. Best-effort and cached — a failed
     * probe just leaves the duration unknown (bubble falls back to the
     * ViewModel's known duration or 0:00).
     */
    fun ensureDuration(messageId: String, url: String) {
        if (_durations.value.containsKey(messageId)) return
        synchronized(probing) {
            if (!probing.add(messageId)) return
        }
        scope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(url, HashMap<String, String>())
                val ms = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                if (ms != null && ms > 0) {
                    _durations.update { it + (messageId to (ms / 1000).toInt()) }
                }
            } catch (_: Exception) {
                // probe failure is silent — duration stays unknown
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
                synchronized(probing) { probing.remove(messageId) }
            }
        }
    }

    private fun applySpeed(mp: MediaPlayer, speed: Float) {
        try {
            mp.playbackParams = PlaybackParams().setSpeed(speed)
        } catch (_: Exception) {
            // PlaybackParams can throw in odd states — speed just stays 1x.
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(100)
                val mp = player ?: break
                val pos = try {
                    mp.currentPosition
                } catch (_: Exception) {
                    break
                }
                _state.update { it.copy(positionMs = pos) }
            }
        }
    }

    private fun stopInternal() {
        tickerJob?.cancel()
        tickerJob = null
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
    }
}
