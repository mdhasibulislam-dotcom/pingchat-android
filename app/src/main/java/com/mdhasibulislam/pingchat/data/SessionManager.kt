package com.mdhasibulislam.pingchat.data

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.RealtimeChannel
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

/** The signed-in user, as the screens see it (uid + email). */
data class SessionUser(val uid: String, val email: String?)

/**
 * App-wide session state: the signed-in user, my profile, who is online,
 * and the global messages feed. Started once from MainActivity.
 *
 * Supabase version: GoTrue sessionStatus drives the session, presence is
 * the Realtime "online" channel (plus the pingchat_profiles.online column,
 * flipped on app foreground / background), and the feed tick comes from
 * postgres-changes on pingchat_messages.
 */
object SessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val authRepo = AuthRepository()
    private val chatRepo = ChatRepository()

    private val _user = MutableStateFlow<SessionUser?>(null)
    val user: StateFlow<SessionUser?> = _user.asStateFlow()

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

    /** Emitted whenever any of my conversations changes (new message, etc.). */
    private val _feedTick = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val feedTick: SharedFlow<Unit> = _feedTick.asSharedFlow()

    /**
     * Emitted after MY OWN mark-read lands (v3.0.2): a participants
     * last_read_at UPDATE fires no messages-feed tick, so without this
     * the chats list kept the stale unread count until the next message
     * event or tab re-entry. The chats list re-reads on this.
     */
    private val _readTick = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val readTick: SharedFlow<Unit> = _readTick.asSharedFlow()

    /** Poke the chats list after a successful mark-read. */
    fun notifyRead() {
        _readTick.tryEmit(Unit)
    }

    private var presenceChannel: RealtimeChannel? = null
    private var feedChannel: RealtimeChannel? = null
    private var presenceJob: Job? = null
    private var feedJob: Job? = null
    private var presenceObserver: DefaultLifecycleObserver? = null
    private var myPresenceState: PresenceState? = null
    private var started = false

    // Application context, for reading Prefs (notifications toggle) without
    // holding an Activity. Set by MainActivity via start(context).
    private var appContext: Context? = null

    fun start(context: Context? = null) {
        if (context != null) appContext = context.applicationContext
        if (started) return
        started = true
        scope.launch {
            try {
                Supabase.client.auth.sessionStatus.collect { status ->
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            _isLoggedIn.value = true
                            status.session.user?.let { onSignedIn(it) }
                        }
                        is SessionStatus.NotAuthenticated,
                        is SessionStatus.RefreshFailure -> {
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
        if (_user.value?.uid == user.id && _profile.value != null) return
        _user.value = SessionUser(user.id, user.email)
        val profile = try {
            authRepo.ensureProfile(user)
        } catch (e: Exception) {
            null
        }
        _profile.value = profile
        setOnline(user.id, true)
        saveFcmToken(user.id)
        startPresenceObserver(user.id)
        startPresence(user, profile?.fullName ?: "")
        startFeed()
    }

    private suspend fun onSignedOut() {
        val uid = _user.value?.uid
        if (uid != null) {
            setOnline(uid, false)
        }
        stopPresenceObserver()
        stopPresence()
        stopFeed()
        if (_user.value == null && _profile.value == null) return
        _isLoggedIn.value = false
        _user.value = null
        _profile.value = null
        _onlineIds.value = emptySet()
    }

    /** pingchat_profiles.online = true/false (+ last_seen when going offline). */
    private suspend fun setOnline(uid: String, online: Boolean) {
        try {
            Supabase.client.from("pingchat_profiles").update({
                set("online", online)
                if (!online) set("last_seen", nowIso())
            }) {
                filter { eq("id", uid) }
            }
        } catch (e: Exception) {
            // presence is best-effort
        }
    }

    /** Save this device's FCM token on my profile (best-effort, never blocks auth). */
    private suspend fun saveFcmToken(uid: String) {
        // v3.0.2: notifications OFF in Settings — do not re-save the token
        // on sign-in / restart, or turning it off would be undone.
        val ctx = appContext
        if (ctx != null && !Prefs(ctx).notificationsEnabled) return
        try {
            val token = Supabase.fcmToken() ?: return
            Supabase.client.from("pingchat_profiles").update({
                set("fcm_token", token)
            }) {
                filter { eq("id", uid) }
            }
        } catch (e: Exception) {
            // push token is best-effort
        }
    }

    /** Flip the online column when the app goes to the foreground / background. */
    private fun startPresenceObserver(uid: String) {
        stopPresenceObserver()
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch {
                    setOnline(uid, true)
                    // The socket may have dropped while the app was
                    // backgrounded: re-announce my presence so peers see me
                    // online again. (The DB column above is not what the UI
                    // reads — Realtime presence is — so the re-track is the
                    // part that matters here.)
                    val channel = presenceChannel
                    val state = myPresenceState
                    if (channel != null && state != null) {
                        chatRepo.trackPresence(channel, state)
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                scope.launch { setOnline(uid, false) }
            }
        }
        presenceObserver = observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    private fun stopPresenceObserver() {
        val observer = presenceObserver ?: return
        presenceObserver = null
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
        } catch (e: Exception) {
        }
    }

    /** Live set of online user ids (Realtime presence on the "online" channel). */
    private fun startPresence(user: UserInfo, name: String) {
        stopPresence()
        scope.launch {
            val channel = try {
                chatRepo.presenceChannel(user.id)
            } catch (e: Exception) {
                return@launch
            }
            presenceChannel = channel
            myPresenceState = PresenceState(user.id, name)
            // Attach the listener FIRST, then subscribe + track. The server
            // sends the full presence_state (everyone already online) as
            // soon as we subscribe, and presenceChangeFlow only emits
            // changes that arrive after the listener is attached. v3.0.0 /
            // v3.0.1 subscribed inside presenceChannel() — before this
            // collector existed — so the initial sync was dropped and nobody
            // ever appeared online. v1 (commit 390a43f) attached the
            // collector before subscribing; this restores that ordering.
            // On Dispatchers.Main.immediate this launch runs undispatched up
            // to its first suspension, i.e. the presence callback is
            // registered before subscribe() below is called.
            presenceJob = scope.launch {
                try {
                    chatRepo.presenceFlow(channel).collect { (joined, left) ->
                        _onlineIds.update { it + joined - left }
                    }
                } catch (e: Exception) {
                    // presence is best-effort
                }
            }
            try {
                chatRepo.subscribePresence()
                chatRepo.trackPresence(channel, PresenceState(user.id, name))
            } catch (e: Exception) {
                // offline — presence stays empty
            }
        }
    }

    private fun stopPresence() {
        presenceJob?.cancel()
        presenceJob = null
        presenceChannel = null
        myPresenceState = null
        scope.launch {
            try {
                chatRepo.dropChannel("online")
            } catch (e: Exception) {
            }
        }
    }

    /** Any change to pingchat_messages pokes the chats list + open chat. */
    private fun startFeed() {
        stopFeed()
        scope.launch {
            val channel = try {
                chatRepo.messagesFeedChannel()
            } catch (e: Exception) {
                return@launch
            }
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
            }
        }
    }

    private fun stopFeed() {
        feedJob?.cancel()
        feedJob = null
        feedChannel = null
        scope.launch {
            try {
                chatRepo.dropChannel("messages-feed")
            } catch (e: Exception) {
            }
        }
    }

    /** Update my cached profile (e.g. after editing it). */
    fun setProfile(profile: Profile?) {
        _profile.value = profile
    }
}
