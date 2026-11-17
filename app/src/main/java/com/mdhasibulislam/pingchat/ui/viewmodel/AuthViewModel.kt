package com.mdhasibulislam.pingchat.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdhasibulislam.pingchat.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val googleLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    /** Registration finished but email confirmation is required. */
    val needsConfirmation: Boolean = false,
    /** Username availability hint on the register screen. */
    val usernameHint: String? = null,
    val usernameOk: Boolean = false
)

/** Login / register / forgot-password. Mirrors js/auth.js. */
class AuthViewModel : ViewModel() {
    private val repo = AuthRepository()

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val emailRe = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    private val usernameRe = Regex("^[a-z0-9_]{3,20}$")

    companion object {
        /**
         * Hand-off from MainActivity.onActivityResult: the Google ID token (or
         * an error message) arrives here because the activity owns the
         * startActivityForResult call. The auth route collects these flows.
         */
        val pendingGoogleToken = MutableStateFlow<String?>(null)
        val pendingGoogleError = MutableStateFlow<String?>(null)
    }

    fun clearMessage() = _state.update { it.copy(error = null, info = null, needsConfirmation = false) }

    fun login(email: String, password: String) {
        if (!emailRe.matches(email.trim())) {
            _state.update { it.copy(error = "Enter a valid email address.") }
            return
        }
        if (password.isEmpty()) {
            _state.update { it.copy(error = "Enter your password.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                repo.signIn(email.trim(), password)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = AuthRepository.friendlyError(e)) }
                return@launch
            }
            _state.update { it.copy(loading = false) }
            // SessionManager picks up the new session and routes to chats.
        }
    }

    fun checkUsername(username: String) {
        val v = username.trim().lowercase()
        if (v.isEmpty()) {
            _state.update { it.copy(usernameHint = "3–20 characters: letters, numbers, underscore.", usernameOk = false) }
            return
        }
        if (!usernameRe.matches(v)) {
            _state.update { it.copy(usernameHint = "Use 3–20 letters, numbers or underscores.", usernameOk = false) }
            return
        }
        // Availability can only be checked once signed in (the Firestore
        // rules deny reads to signed-out users), so the real check happens
        // inside register() — here only the format is validated.
        _state.update { it.copy(usernameHint = "@$v looks good — availability is checked when you register.", usernameOk = true) }
    }

    fun register(name: String, username: String, email: String, password: String, password2: String) {
        val uname = username.trim().lowercase()
        when {
            name.trim().length < 2 -> return _state.update { it.copy(error = "Enter your full name.") }
            !usernameRe.matches(uname) -> return _state.update { it.copy(error = "Username must be 3–20 letters, numbers or underscores.") }
            !emailRe.matches(email.trim()) -> return _state.update { it.copy(error = "Enter a valid email address.") }
            password.length < 8 -> return _state.update { it.copy(error = "Password must be at least 8 characters.") }
            password != password2 -> return _state.update { it.copy(error = "Passwords do not match.") }
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                // signUp creates the account first and checks the username
                // afterwards (the check needs a signed-in user — checking
                // before sign-up was always denied and wrongly reported as
                // "already taken"). If the name is taken, signUp rolls the
                // new account back and throws.
                repo.signUp(name.trim(), uname, email.trim(), password)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = AuthRepository.friendlyError(e)) }
                return@launch
            }
            // Firebase signs the new user in immediately — no confirmation
            // link step. SessionManager picks up the session and routes
            // to chats.
            _state.update { it.copy(loading = false) }
        }
    }

    fun resetPassword(email: String) {
        if (!emailRe.matches(email.trim())) {
            _state.update { it.copy(error = "Enter a valid email address.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, info = null) }
            try {
                repo.resetPassword(email.trim())
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = AuthRepository.friendlyError(e)) }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false,
                    info = "If an account exists for that email, a reset link is on its way."
                )
            }
        }
    }

    /** Called by MainActivity with the Google ID token from Google Sign-In. */
    fun googleSignIn(idToken: String) {
        viewModelScope.launch {
            _state.update { it.copy(googleLoading = true, error = null) }
            try {
                repo.signInWithGoogle(idToken)
            } catch (e: Exception) {
                _state.update { it.copy(googleLoading = false, error = AuthRepository.friendlyError(e)) }
                return@launch
            }
            _state.update { it.copy(googleLoading = false) }
        }
    }

    fun googleError(message: String) {
        _state.update { it.copy(googleLoading = false, error = message) }
    }
}
