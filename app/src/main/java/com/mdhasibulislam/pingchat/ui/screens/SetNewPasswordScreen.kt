package com.mdhasibulislam.pingchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * v3.2.2 deep-link fix: the password-reset link opens the app (not a
 * black browser page) and lands here. The reset link's session is
 * already imported by MainActivity, so [AuthRepository.updatePassword]
 * works on the signed-in session. On success a confirmation shows
 * briefly, then [onDone] routes to the chats.
 *
 * The signed-in Change Password screen (Settings > Privacy & Security)
 * is untouched and keeps working as before.
 */
@Composable
fun SetNewPasswordScreen(onDone: () -> Unit) {
    val colors = pingChatColors
    val authRepo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    LaunchedEffect(success) {
        if (success) {
            delay(1500)
            onDone()
        }
    }

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
        PingChatWordmark(baseSize = 28.sp, baseColor = colors.text)
        Spacer(Modifier.height(4.dp))
        Text(
            "Choose a new password",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        if (success) {
            Text(
                "Password updated — signing you in…",
                color = Emerald,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        } else {
            PasswordField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "New password"
            )
            Spacer(Modifier.height(12.dp))
            PasswordField(
                value = password2,
                onValueChange = { password2 = it; error = null },
                label = "Confirm new password"
            )
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    when {
                        password.length < 8 -> {
                            error = "Password must be at least 8 characters."
                            return@Button
                        }
                        password != password2 -> {
                            error = "Passwords do not match."
                            return@Button
                        }
                    }
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            authRepo.updatePassword(password)
                            success = true
                        } catch (e: Exception) {
                            error = AuthRepository.friendlyError(e)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Save new password", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
