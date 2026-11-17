package com.mdhasibulislam.pingchat.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * App-wide session state: the signed-in user, my profile, who is online,
 * and the global messages feed. Started once from MainActivity.
 *
 * Firebase version: AuthStateListener drives the session, presence is the
 * users/{uid}.online field (flipped on app foreground / background via
 * ProcessLifecycleOwner), online users come from a Firestore query, and
 * the feed tick comes from a listener on my conversations.
 */
object SessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val authRepo = AuthRepository()

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> = _user.asStateFlow()

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

    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var onlineListener: ListenerRegistration? = null
    private var feedListener: ListenerRegistration? = null
    private var presenceObserver: DefaultLifecycleObserver? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val firebaseUser = auth.currentUser
            if (firebaseUser != null) {
                _isLoggedIn.value = true
                scope.launch { onSignedIn(firebaseUser) }
            } else {
                _isLoggedIn.value = false
                scope.launch { onSignedOut() }
            }
        }
        authListener = listener
        FirebaseDb.auth.addAuthStateListener(listener)
        // First emission: Firebase restores the saved session before the
        // listener fires; until then isLoggedIn stays null (loading).
        val current = FirebaseDb.auth.currentUser
        if (current != null) {
            _isLoggedIn.value = true
            scope.launch { onSignedIn(current) }
        }
    }

    private suspend fun onSignedIn(user: FirebaseUser) {
        if (_user.value?.uid == user.uid && _profile.value != null) return
        _user.value = user
        val profile = try {
            authRepo.ensureProfile(user)
        } catch (e: Exception) {
            null
        }
        _profile.value = profile
        setOnline(user.uid, true)
        saveFcmToken(user.uid)
        startPresenceObserver(user.uid)
        startOnlineListener()
        startFeedListener(user.uid)
    }

    private suspend fun onSignedOut() {
        val uid = _user.value?.uid
        if (uid != null) {
            setOnline(uid, false)
        }
        stopPresenceObserver()
        stopOnlineListener()
        stopFeedListener()
        if (_user.value == null && _profile.value == null) return
        _isLoggedIn.value = false
        _user.value = null
        _profile.value = null
        _onlineIds.value = emptySet()
    }

    /** users/{uid}.online = true/false (+ lastSeen when going offline). */
    private suspend fun setOnline(uid: String, online: Boolean) {
        try {
            val data = if (online) {
                mapOf<String, Any?>("online" to true)
            } else {
                mapOf<String, Any?>("online" to false, "lastSeen" to nowMillis())
            }
            FirebaseDb.db.collection("users").document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge()).await()
        } catch (e: Exception) {
            // presence is best-effort
        }
    }

    /** Save this device's FCM token on my profile (best-effort, never blocks auth). */
    private suspend fun saveFcmToken(uid: String) {
        try {
            val token = FirebaseDb.messaging.token.await()
            FirebaseDb.db.collection("users").document(uid)
                .set(
                    mapOf<String, Any?>("fcmToken" to token),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            // push token is best-effort
        }
    }

    /** Flip presence when the app goes to the foreground / background. */
    private fun startPresenceObserver(uid: String) {
        stopPresenceObserver()
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch { setOnline(uid, true) }
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

    /** Live set of online user ids (any user with online == true). */
    private fun startOnlineListener() {
        stopOnlineListener()
        onlineListener = FirebaseDb.db.collection("users")
            .whereEqualTo("online", true)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    _onlineIds.value = snap.documents.map { it.id }.toSet()
                }
            }
    }

    private fun stopOnlineListener() {
        onlineListener?.remove()
        onlineListener = null
    }

    /** Any change to one of my conversations pokes the chats list + open chat. */
    private fun startFeedListener(uid: String) {
        stopFeedListener()
        feedListener = FirebaseDb.db.collection("conversations")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { _, _ ->
                _feedTick.tryEmit(Unit)
            }
    }

    private fun stopFeedListener() {
        feedListener?.remove()
        feedListener = null
    }

    /** Update my cached profile (e.g. after editing it). */
    fun setProfile(profile: Profile?) {
        _profile.value = profile
    }
}
