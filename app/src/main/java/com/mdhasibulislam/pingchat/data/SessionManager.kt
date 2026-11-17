package com.mdhasibulislam.pingchat.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App-wide session state: the signed-in user, my profile, who is online,
 * and the global messages feed. Started once from MainActivity.
 *
 * Mirrors the web app's boot (js/app.js): on sign-in it ensures the profile,
 * links OneSignal to the user, starts presence and the messages feed.
 */
object SessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val chatRepo = ChatRepository()
    private val authRepo = AuthRepository()

    private val _user = MutableStateFlow<UserInfo?>(null)
    val user: StateFlow<UserInfo?> = _user.asStateFlow()

    /**
     * Tri-state login flag for navigation: null while the saved session is
     * still loading, true when signed in, false when signed out.
     */
    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn.asStateFlow()

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _onlineIds = MutableStateFlow<Set<String>>(emptySet())
    val onlineIds: StateFlow<Set<String>> = _onlineIds.asStateFlow()

    /** Emitted whenever any message is inserted/updated/deleted anywhere. */
    private val _feedTick = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val feedTick: SharedFlow<Unit> = _feedTick.asSharedFlow()

    private var presenceChannel: RealtimeChannel? = null
    private var feedChannel: RealtimeChannel? = null
    private var presenceJob: Job? = null
    private var feedJob: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            try {
                Supabase.client.auth.sessionStatus.collect { status ->
                    when (status) {
                        is io.github.jan.supabase.auth.status.SessionStatus.Authenticated -> {
                            _isLoggedIn.value = true
                            status.session.user?.let { onSignedIn(it) }
                        }
                        is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated,
                        is io.github.jan.supabase.auth.status.SessionStatus.RefreshFailure -> {
                            _isLoggedIn.value = false
                            onSignedOut()
                        }
                        // Initializing / refreshing — keep the previous value.
                        else -> Unit
                    }
                }
            } catch (e: Exception) {
                // The client could not start (e.g. no network engine) — stay on
                // the login screen instead of crashing the app at launch.
                _isLoggedIn.value = false
            }
        }
    }

    private suspend fun onSignedIn(user: UserInfo) {
        if (_user.value?.id == user.id && _profile.value != null) return
        _user.value = user
        val profile = try {
            authRepo.ensureProfile(user)
        } catch (e: Exception) {
            null
        }
        _profile.value = profile
        PushHelper.login(user.id, profile?.username)
        startPresence(user, profile?.fullName ?: "")
        startFeed()
    }

    private suspend fun onSignedOut() {
        if (_user.value == null && _profile.value == null) return
        _isLoggedIn.value = false
        stopPresence()
        stopFeed()
        _user.value = null
        _profile.value = null
        _onlineIds.value = emptySet()
    }

    private fun startPresence(user: UserInfo, name: String) {
        stopPresence()
        val channel = chatRepo.presenceChannel(user.id)
        presenceChannel = channel
        presenceJob = scope.launch {
            try {
                chatRepo.presenceFlow(channel).collect { (joined, left) ->
                    _onlineIds.update { it + joined - left }
                }
            } catch (e: Exception) {
                // presence is best-effort
            }
        }
        scope.launch {
            try {
                channel.subscribe(true)
                chatRepo.trackPresence(channel, PresenceState(user.id, name))
            } catch (e: Exception) {
                // offline — presence stays empty
            }
        }
    }

    private fun startFeed() {
        stopFeed()
        val channel = chatRepo.messagesFeedChannel()
        feedChannel = channel
        feedJob = scope.launch {
            launch {
                try {
                    chatRepo.messagesInsertFlow(channel).collect { _feedTick.tryEmit(Unit) }
                } catch (e: Exception) {
                }
            }
            launch {
                try {
                    chatRepo.messagesUpdateFlow(channel).collect { _feedTick.tryEmit(Unit) }
                } catch (e: Exception) {
                }
            }
            launch {
                try {
                    chatRepo.messagesDeleteFlow(channel).collect { _feedTick.tryEmit(Unit) }
                } catch (e: Exception) {
                }
            }
            try {
                channel.subscribe()
            } catch (e: Exception) {
                // offline — feed stays quiet until reconnect
            }
        }
    }

    private fun stopPresence() {
        presenceJob?.cancel()
        presenceJob = null
        val channel = presenceChannel
        presenceChannel = null
        if (channel != null) {
            scope.launch {
                try {
                    channel.unsubscribe()
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun stopFeed() {
        feedJob?.cancel()
        feedJob = null
        val channel = feedChannel
        feedChannel = null
        if (channel != null) {
            scope.launch {
                try {
                    channel.unsubscribe()
                } catch (e: Exception) {
                }
            }
        }
    }

    /** Update my cached profile (e.g. after editing it). */
    fun setProfile(profile: Profile?) {
        _profile.value = profile
    }
}
