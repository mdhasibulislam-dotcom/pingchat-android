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
    /** Email the confirmation link was sent to (verification screen). */
    val pendingVerificationEmail: String? = null,
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

        /**
         * Hand-off from MainActivity's deep-link handling: an expired or
         * invalid auth link (e.g. a stale password-reset link) arrives
         * here so the login screen can show it. Same pattern as the
         * Google hand-off above.
         */
        val pendingDeepLinkError = MutableStateFlow<String?>(null)
    }

    fun clearMessage() = _state.update {
        it.copy(error = null, info = null, needsConfirmation = false, pendingVerificationEmail = null)
    }

    /** Show a deep-link error (e.g. expired reset link) on the auth screens. */
    fun deepLinkError(message: String) {
        _state.update { it.copy(error = message, info = null) }
    }

    fun login(login: String, password: String) {
        val id = login.trim()
        when {
            id.isEmpty() ->
                return _state.update { it.copy(error = "Enter your email or username.") }
            id.contains("@") && !emailRe.matches(id) ->
                return _state.update { it.copy(error = "Enter a valid email address.") }
            !id.contains("@") && !usernameRe.matches(id.lowercase()) ->
                return _state.update { it.copy(error = "Enter a valid email or username.") }
            password.isEmpty() ->
                return _state.update { it.copy(error = "Enter your password.") }
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                // A username (no "@") is resolved to its email first;
                // an email signs in directly. No phone-number login.
                repo.signInWithUsernameOrEmail(id, password)
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
        // Live availability check (the RPC works before sign-in).
        _state.update { it.copy(usernameHint = "Checking @$v…", usernameOk = false) }
        viewModelScope.launch {
            val free = try {
                repo.isUsernameAvailable(v)
            } catch (e: Exception) {
                null
            }
            _state.update {
                when (free) {
                    true -> it.copy(usernameHint = "@$v is available.", usernameOk = true)
                    false -> it.copy(usernameHint = "@$v is already taken.", usernameOk = false)
                    null -> it.copy(usernameHint = "@$v looks good — availability is checked when you register.", usernameOk = true)
                }
            }
        }
    }

    /**
     * [dob] is the ISO yyyy-MM-dd date of birth from the register
     * screen's date picker. Required, and must be a real past date.
     */
    fun register(
        name: String,
        username: String,
        email: String,
        password: String,
        password2: String,
        dob: String? = null
    ) {
        val uname = username.trim().lowercase()
        val dobDate = try {
            dob?.let { java.time.LocalDate.parse(it) }
        } catch (e: Exception) {
            null
        }
        when {
            name.trim().length < 2 -> return _state.update { it.copy(error = "Enter your full name.") }
            !usernameRe.matches(uname) -> return _state.update { it.copy(error = "Username must be 3–20 letters, numbers or underscores.") }
            !emailRe.matches(email.trim()) -> return _state.update { it.copy(error = "Enter a valid email address.") }
            dob.isNullOrBlank() -> return _state.update { it.copy(error = "Enter your date of birth.") }
            dobDate == null -> return _state.update { it.copy(error = "Enter a real date of birth.") }
            !dobDate.isBefore(java.time.LocalDate.now()) -> return _state.update { it.copy(error = "Date of birth must be in the past.") }
            password.length < 8 -> return _state.update { it.copy(error = "Password must be at least 8 characters.") }
            password != password2 -> return _state.update { it.copy(error = "Passwords do not match.") }
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val signedIn = try {
                // The username is checked before the account is created
                // (the RPC works before sign-in), so a taken name never
                // creates a stray account.
                repo.signUp(name.trim(), uname, email.trim(), password, dob)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = AuthRepository.friendlyError(e)) }
                return@launch
            }
            if (!signedIn) {
                // "Confirm email" is ON (v3.2.2): the account exists but
                // the user must confirm first. MainActivity switches to
                // the verification screen on needsConfirmation.
                _state.update {
                    it.copy(
                        loading = false,
                        needsConfirmation = true,
                        pendingVerificationEmail = email.trim(),
                        info = null
                    )
                }
                return@launch
            }
            // Supabase signs the new user in immediately — no confirmation
            // link step. SessionManager picks up the session and routes
            // to chats.
            _state.update { it.copy(loading = false) }
        }
    }

    /**
     * Resend the signup confirmation email (verification screen).
     * Supabase enforces a 60-second cooldown — the screen disables its
     * button for 60s; a server refusal lands in state.error.
     */
    fun resendVerificationEmail(email: String) {
        viewModelScope.launch {
            _state.update { it.copy(error = null, info = null) }
            try {
                repo.resendConfirmationEmail(email.trim())
            } catch (e: Exception) {
                _state.update { it.copy(error = AuthRepository.friendlyError(e)) }
                return@launch
            }
            _state.update { it.copy(info = "Verification email sent.") }
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
