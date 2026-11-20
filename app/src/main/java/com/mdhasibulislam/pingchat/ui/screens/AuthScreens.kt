package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.EmeraldSoft
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/** Login / register fields are pill-shaped (rounded), not boxy rectangles. */
private val AuthFieldShape = RoundedCornerShape(50)

/** Shared auth chrome: logo, title, error/info banners. */
@Composable
private fun AuthShell(title: String, subtitle: String, content: @Composable () -> Unit) {
    val colors = pingChatColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo: emerald rounded square with a chat bubble (drawn as text glyph-free:
        // a simple emerald box with "P"). The launcher icon carries the real mark.
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(72.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(64.dp)) {
                drawRoundRect(color = Emerald, cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f))
            }
            Text("P", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("PingChat", style = MaterialTheme.typography.headlineMedium, color = colors.text)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun AuthMessage(state: com.mdhasibulislam.pingchat.ui.viewmodel.AuthUiState) {
    val colors = pingChatColors
    if (state.error != null) {
        Text(
            text = state.error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textAlign = TextAlign.Center
        )
    }
    if (state.info != null) {
        Text(
            text = state.info,
            color = Emerald,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = AuthFieldShape,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password"
                )
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun GoogleButton(loading: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !loading,
        shape = AuthFieldShape,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            // Multicolour "G" drawn as text — the launcher icon carries the real mark.
            Text("G", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Emerald)
            Spacer(Modifier.size(8.dp))
            Text("Continue with Google")
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.material3.Divider(modifier = Modifier.weight(1f))
        Text("or", color = pingChatColors.muted, style = MaterialTheme.typography.bodySmall)
        androidx.compose.material3.Divider(modifier = Modifier.weight(1f))
    }
}

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onGoogleClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onForgotClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val colors = pingChatColors
    // Mockup screen 8: own centered column (NOT AuthShell, which is shared with
    // Register/Forgot) — green rounded-square "P" logo, "PingChat" title,
    // "Connect • Chat • Share" subtitle, form, Login, Create Account,
    // "or continue with" divider, Continue with Google.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(72.dp)
                .background(Emerald, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("P", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "PingChat",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Connect • Chat • Share",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        AuthMessage(state)
        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text("Email or username") },
            singleLine = true,
            shape = AuthFieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(value = password, onValueChange = { password = it }, label = "Password")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onForgotClick) { Text("Forgot password?") }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.login(login, password) },
            enabled = !state.loading,
            shape = AuthFieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (state.loading) CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
            else Text("Login", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRegisterClick,
            shape = AuthFieldShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Create Account", fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.material3.Divider(modifier = Modifier.weight(1f))
            Text("or continue with", color = colors.muted, style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.Divider(modifier = Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = onGoogleClick,
            enabled = !state.googleLoading,
            shape = AuthFieldShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (state.googleLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                // Google "G" in Google blue — no emoji, no image asset.
                Text("G", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF4285F4))
                Spacer(Modifier.size(8.dp))
                Text("Continue with Google", color = colors.text)
            }
        }
    }
}

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onGoogleClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    AuthShell(title = "Create account", subtitle = "Join PingChat") {
        AuthMessage(state)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full name") },
            singleLine = true,
            shape = AuthFieldShape,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                viewModel.checkUsername(it)
            },
            label = { Text("Username") },
            singleLine = true,
            shape = AuthFieldShape,
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                Text(
                    state.usernameHint ?: "3–20 characters: letters, numbers, underscore.",
                    color = if (state.usernameOk) Emerald else pingChatColors.muted
                )
            }
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            shape = AuthFieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(value = password, onValueChange = { password = it }, label = "Password")
        Spacer(Modifier.height(12.dp))
        PasswordField(value = password2, onValueChange = { password2 = it }, label = "Confirm password")
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.register(name, username, email, password, password2) },
            enabled = !state.loading,
            shape = AuthFieldShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Create account")
        }
        OrDivider()
        GoogleButton(loading = state.googleLoading, onClick = onGoogleClick)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onLoginClick) { Text("Log in") }
        }
    }
}

@Composable
fun ForgotPasswordScreen(viewModel: AuthViewModel, onBackToLogin: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    AuthShell(title = "Reset password", subtitle = "We will email you a reset link") {
        AuthMessage(state)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            shape = AuthFieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.resetPassword(email) },
            enabled = !state.loading,
            shape = AuthFieldShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Send reset link")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBackToLogin) { Text("Back to log in") }
    }
}

/**
 * Two-factor login challenge (v3.2.0) — the gate for an AAL1 session.
 * Shown instead of the chats while SessionManager.mfaChallengePending
 * is true (MainActivity routes here). Same 6-digit code field as
 * TwoFactorScreen's verify UI: a wrong code stays on this screen with
 * an error and can be retried; verifying promotes the session to AAL2
 * and SessionManager.onMfaVerified() continues the sign-in; "Cancel
 * sign-in" signs out, which routes back to the login screen.
 */
@Composable
fun MfaChallengeScreen() {
    val colors = pingChatColors
    val authRepo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(EmeraldSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = Emerald,
                modifier = Modifier.size(52.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Two-factor authentication",
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This account has two-factor authentication on. " +
                "Enter the 6-digit code from your authenticator app to finish signing in.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter { c -> c.isDigit() }.take(6); error = null },
            label = { Text("6-digit code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = AuthFieldShape,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (code.length != 6) {
                    error = "Enter the 6-digit code from your authenticator app."
                    return@Button
                }
                busy = true
                error = null
                scope.launch {
                    try {
                        authRepo.verifyLoginChallenge(code)
                        // Session is AAL2 now — continue the sign-in
                        // that was held back (profile, presence, feed).
                        SessionManager.onMfaVerified()
                    } catch (e: Exception) {
                        error = AuthRepository.friendlyError(e)
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            shape = AuthFieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text("Verify", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                // Cancel = sign out: SessionManager flips isLoggedIn
                // to false and MainActivity routes back to login.
                scope.launch {
                    try {
                        authRepo.signOut()
                    } catch (e: Exception) {
                        // sign-out never blocks leaving this screen
                    }
                }
            },
            enabled = !busy
        ) {
            Text("Cancel sign-in", color = MaterialTheme.colorScheme.error)
        }
    }
}
