package com.mdhasibulislam.pingchat.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.EmeraldSoft
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import kotlinx.coroutines.launch

/** Render [text] as a black-on-white QR bitmap (ZXing core, free).
 *  Copied from PublicProfileScreen.kt (same helper, kept local so the
 *  screens do not import across each other). */
private fun qrBitmap(text: String, size: Int = 512): Bitmap? {
    return try {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

/** Display form of a TOTP secret: grouped in 4s ("ABCD EFGH …") so it
 *  is easy to read and type by hand. Display-only — the Copy button
 *  copies the raw secret with no spaces. */
private fun groupedSecret(secret: String): String =
    secret.chunked(4).joinToString(" ")

/**
 * Two-Factor Authentication (v3.2.0) — REAL TOTP via supabase-kt 3.9.0
 * auth-kt (auth.mfa): enroll returns the otpauth URI + secret, the QR
 * code is rendered from that URI, and the 6-digit code from the user's
 * authenticator app verifies the factor (challenge + verify, which
 * promotes the session to AAL2). The secret is also shown grouped in
 * monospace with a Copy button, for authenticator apps that cannot
 * scan the QR. Disabling asks for a current code
 * first, because unenrolling a verified factor needs an AAL2 session.
 *
 * NOTE: TOTP must be enabled in the Supabase dashboard (Auth > MFA)
 * or enrollment fails with an honest "not turned on yet" message —
 * nothing here is faked.
 */
@Composable
fun TwoFactorScreen(onBack: () -> Unit) {
    val colors = pingChatColors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val authRepo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var enabledFactor by remember { mutableStateOf<AuthRepository.MfaFactorInfo?>(null) }
    var enrollment by remember { mutableStateOf<AuthRepository.MfaEnrollment?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                enabledFactor = authRepo.listVerifiedMfaFactors().firstOrNull()
            } catch (e: Exception) {
                error = "Could not check your two-factor status. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Row(
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
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Two-Factor Authentication",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                fontWeight = FontWeight.Bold
            )
        }

        if (loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

                val factor = enabledFactor
                val pending = enrollment
                when {
                    // ---------- 2FA is ON ----------
                    factor != null && pending == null -> {
                        Text(
                            text = "Two-factor authentication is on.",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Your account is protected with an authenticator app" +
                                (factor.friendlyName?.let { " ($it)" } ?: "") +
                                ". You will be asked for a 6-digit code when you sign in on a new device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text2,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "To turn it off, enter a current code from your authenticator app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text2,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.filter { c -> c.isDigit() }.take(6); error = null },
                            label = { Text("6-digit code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                if (code.length != 6) {
                                    error = "Enter the 6-digit code from your authenticator app."
                                    return@OutlinedButton
                                }
                                busy = true
                                error = null
                                scope.launch {
                                    try {
                                        authRepo.disableTotpFactor(factor.id, code)
                                        code = ""
                                        info = "Two-factor authentication is off."
                                        refresh()
                                    } catch (e: Exception) {
                                        error = AuthRepository.friendlyError(e)
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text("Turn Off Two-Factor Authentication", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    // ---------- Enrolling: show QR + secret, verify code ----------
                    pending != null -> {
                        Text(
                            text = "Scan this code with your authenticator app.",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Use Google Authenticator, Authy, or any authenticator app. " +
                                "Then enter the 6-digit code it shows to finish setup.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text2,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        val qr = remember(pending.qrUri) { qrBitmap(pending.qrUri) }
                        if (qr != null) {
                            Image(
                                bitmap = qr.asImageBitmap(),
                                contentDescription = "QR code to scan with your authenticator app",
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(12.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Or enter this secret by hand:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text2
                        )
                        Spacer(Modifier.height(8.dp))
                        // Secret key, grouped in 4s in monospace so it is
                        // easy to read/type, with a Copy button for
                        // authenticator apps that cannot scan the QR.
                        // Displayed grouped, but the RAW secret (no
                        // spaces) is what gets copied — it is the same
                        // pending.secret the QR code was rendered from.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.card, RoundedCornerShape(12.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = groupedSecret(pending.secret),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = colors.text,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(pending.secret))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Copy")
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.filter { c -> c.isDigit() }.take(6); error = null },
                            label = { Text("6-digit code") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                        authRepo.verifyTotpCode(pending.factorId, code)
                                        enrollment = null
                                        code = ""
                                        info = "Two-factor authentication is on."
                                        refresh()
                                    } catch (e: Exception) {
                                        error = AuthRepository.friendlyError(e)
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text("Verify & Enable", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Enter the code soon — an unfinished setup expires after a few minutes, and you will need to start again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            textAlign = TextAlign.Center
                        )
                    }
                    // ---------- 2FA is OFF: offer setup ----------
                    else -> {
                        Text(
                            text = "Add an extra layer of security.",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "With two-factor authentication on, signing in needs your password " +
                                "and a 6-digit code from an authenticator app on your phone — so even " +
                                "if someone learns your password, they still cannot sign in as you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text2,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = {
                                busy = true
                                error = null
                                info = null
                                scope.launch {
                                    try {
                                        enrollment = authRepo.enrollTotpFactor()
                                    } catch (e: Exception) {
                                        error = AuthRepository.friendlyError(e)
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text("Set Up Two-Factor Authentication", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
                if (info != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(info ?: "", color = Emerald, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
