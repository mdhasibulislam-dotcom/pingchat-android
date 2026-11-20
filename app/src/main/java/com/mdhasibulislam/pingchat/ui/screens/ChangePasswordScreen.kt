package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.EmeraldSoft
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import kotlinx.coroutines.launch

/**
 * Change Password (v3.2.0, mockup screen 4): green shield, "Choose a
 * strong password to keep your account safe.", New Password + Confirm
 * New Password with eye toggles, green full-width Update Password
 * button. Wired to AuthRepository.updatePassword (Supabase Auth).
 * Supabase's minimum password length is 6 characters.
 */
@Composable
fun ChangePasswordScreen(onBack: () -> Unit) {
    val colors = pingChatColors
    val authRepo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNew by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        // Top bar (same as the other v3.2.0 settings screens).
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = "Change Password",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Green shield, mockup screen 4.
            androidx.compose.foundation.layout.Box(
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
                text = "Choose a strong password to keep your account safe.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.text2,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; error = null; done = false },
                label = { Text("New Password") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.muted) },
                trailingIcon = {
                    IconButton(onClick = { showNew = !showNew }) {
                        Icon(
                            if (showNew) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (showNew) "Hide password" else "Show password",
                            tint = colors.muted
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; error = null; done = false },
                label = { Text("Confirm New Password") },
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.muted) },
                trailingIcon = {
                    IconButton(onClick = { showConfirm = !showConfirm }) {
                        Icon(
                            if (showConfirm) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (showConfirm) "Hide password" else "Show password",
                            tint = colors.muted
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (done) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Password updated.",
                    color = Emerald,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    when {
                        newPassword.isEmpty() || confirmPassword.isEmpty() ->
                            error = "Enter your new password twice."
                        newPassword.length < 6 ->
                            error = "Password must be at least 6 characters."
                        newPassword != confirmPassword ->
                            error = "Passwords do not match."
                        else -> {
                            error = null
                            busy = true
                            scope.launch {
                                try {
                                    authRepo.updatePassword(newPassword)
                                    newPassword = ""
                                    confirmPassword = ""
                                    done = true
                                } catch (e: Exception) {
                                    error = AuthRepository.friendlyError(e)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("Update Password", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
