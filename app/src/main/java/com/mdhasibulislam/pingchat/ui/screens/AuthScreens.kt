package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
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
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.AuthViewModel

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
    AuthShell(title = "Welcome back", subtitle = "Log in to PingChat") {
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
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.login(login, password) },
            enabled = !state.loading,
            shape = AuthFieldShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Log in")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onForgotClick) { Text("Forgot password?") }
        OrDivider()
        GoogleButton(loading = state.googleLoading, onClick = onGoogleClick)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("No account yet?", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRegisterClick) { Text("Register") }
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
