package com.mdhasibulislam.pingchat.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdhasibulislam.pingchat.data.AuthRepository
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.EmeraldSoft
import com.mdhasibulislam.pingchat.ui.theme.pingChatColors
import com.mdhasibulislam.pingchat.ui.viewmodel.AuthViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Login / register fields are pill-shaped (rounded), not boxy rectangles. */
private val AuthFieldShape = RoundedCornerShape(50)

/** Register fields per the v3.2.2 mockup: slightly rounded rectangles. */
private val RegisterFieldShape = RoundedCornerShape(14.dp)

/**
 * v3.2.2 wordmark: "Ping Chat" (two words, with a space) with "BD" as a
 * small emerald superscript above "Chat" — roughly 35% of the wordmark
 * size, per the approved login mockup (h1 "Ping Chat<sup>BD</sup>").
 * The green rounded P logo stays as-is; the Android launcher label
 * stays plain "PingChat BD" (another worker owns strings.xml).
 */
fun pingChatWordmark(baseSize: TextUnit, baseColor: Color): AnnotatedString =
    buildAnnotatedString {
        withStyle(
            SpanStyle(
                fontSize = baseSize,
                fontWeight = FontWeight.Bold,
                color = baseColor
            )
        ) {
            append("Ping Chat")
        }
        withStyle(
            SpanStyle(
                fontSize = baseSize * 0.35f,
                baselineShift = BaselineShift.Superscript,
                color = Emerald,
                fontWeight = FontWeight.Bold
            )
        ) {
            append("BD")
        }
    }

/** The wordmark as a standalone heading. */
@Composable
fun PingChatWordmark(baseSize: TextUnit, baseColor: Color) {
    Text(text = pingChatWordmark(baseSize, baseColor))
}

/** Shared auth chrome: logo, wordmark, error/info banners. */
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
        PingChatWordmark(
            baseSize = MaterialTheme.typography.headlineMedium.fontSize,
            baseColor = colors.text
        )
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
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    shape: Shape = AuthFieldShape
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        shape = shape,
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

/**
 * v3.2.2 login illustration (login.png): three chat bubbles — dark green
 * "Hello!", light green "Hi there!", pale green "PingChat" — plus a
 * paper-plane vector icon. No emoji: the mockup's 👋 / 💬 glyphs are
 * text-only here, per the real-icons rule.
 */
@Composable
private fun LoginIllustration() {
    val colors = pingChatColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = colors.text,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 2.dp)
                .size(34.dp)
                .rotate(24f)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 8.dp, y = 10.dp)
                .background(Emerald, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text("Hello!", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(y = 6.dp)
                .background(Color(0xFF34D399), RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text("Hi there!", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 56.dp)
                .background(Color(0xFFA7F3D0), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text("PingChat", color = Color(0xFF065F46), fontWeight = FontWeight.Bold)
        }
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
    // Mockup screen 8 (v3.2.2): green rounded-square "P" logo, "Ping Chat"
    // wordmark with emerald superscript "BD", "Connect • Chat • Share"
    // subtitle, chat-bubble illustration, email/username + password form
    // (the mockup shows buttons only, but login must keep working),
    // Login, Create Account, "or continue with" divider, Continue with
    // Google, and the "Ping Chatᴮᴰ • Chat, call & connect" footer.
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
            "Connect • Chat • Share",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        LoginIllustration()
        Spacer(Modifier.height(8.dp))
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
        Spacer(Modifier.height(28.dp))
        Text(
            text = buildAnnotatedString {
                append(pingChatWordmark(13.sp, colors.muted))
                append(" • Chat, call & connect")
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            textAlign = TextAlign.Center
        )
    }
}

/** Register field with its label above it, per the v3.2.2 mockup. */
@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = pingChatColors.text
        )
        Spacer(Modifier.height(7.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onLoginClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var dobIso by remember { mutableStateOf<String?>(null) }
    var showDobPicker by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    val colors = pingChatColors
    // Display DD/MM/YYYY; the API takes ISO yyyy-MM-dd.
    val dobDisplay = dobIso?.let {
        try {
            LocalDate.parse(it).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) {
            it
        }
    } ?: ""
    // Past dates only — a future date can never be a date of birth.
    val dobPickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= System.currentTimeMillis()
        }
    )
    if (showDobPicker) {
        DatePickerDialog(
            onDismissRequest = { showDobPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dobPickerState.selectedDateMillis?.let { millis ->
                        dobIso = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString()
                    }
                    showDobPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDobPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dobPickerState)
        }
    }
    // Mockup (register.png): back arrow + "Create Account" header,
    // subtitle, labelled fields in order Full Name, Username, Email,
    // Date of Birth, Password, Confirm Password, green Create Account
    // button, terms line, "Already have an account? Login". No Google
    // button on this screen in the mockup — Google stays on login.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLoginClick) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to log in",
                    tint = colors.text
                )
            }
            Text(
                "Create Account",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Join PingChat BD — chat, call & share with your friends.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            modifier = Modifier.padding(start = 48.dp, end = 4.dp)
        )
        Spacer(Modifier.height(20.dp))
        AuthMessage(state)
        LabeledField("Full Name") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Hasibul Islam") },
                singleLine = true,
                shape = RegisterFieldShape,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        LabeledField("Username") {
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    viewModel.checkUsername(it)
                },
                placeholder = { Text("hasibulxyz") },
                singleLine = true,
                shape = RegisterFieldShape,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Text("@", color = colors.muted, fontSize = 18.sp)
                },
                supportingText = {
                    Text(
                        state.usernameHint ?: "This is how friends will find you — e.g. @hasibulxyz",
                        color = if (state.usernameOk) Emerald else colors.muted
                    )
                }
            )
        }
        Spacer(Modifier.height(16.dp))
        LabeledField("Email") {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("you@example.com") },
                singleLine = true,
                shape = RegisterFieldShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
        LabeledField("Date of Birth") {
            OutlinedTextField(
                value = dobDisplay,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("DD / MM / YYYY") },
                singleLine = true,
                shape = RegisterFieldShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDobPicker = true },
                trailingIcon = {
                    IconButton(onClick = { showDobPicker = true }) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = "Pick your date of birth",
                            tint = colors.muted
                        )
                    }
                }
            )
        }
        Spacer(Modifier.height(16.dp))
        LabeledField("Password") {
            PasswordField(
                value = password,
                onValueChange = { password = it },
                placeholder = "At least 8 characters",
                shape = RegisterFieldShape
            )
        }
        Spacer(Modifier.height(16.dp))
        LabeledField("Confirm Password") {
            PasswordField(
                value = password2,
                onValueChange = { password2 = it },
                placeholder = "Type the password again",
                shape = RegisterFieldShape
            )
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { viewModel.register(name, username, email, password, password2, dobIso) },
            enabled = !state.loading,
            shape = AuthFieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            if (state.loading) CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
            else Text("Create Account", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = buildAnnotatedString {
                append("By creating an account you agree to our\n")
                withStyle(SpanStyle(color = Emerald, fontWeight = FontWeight.Bold)) {
                    append("Terms of Service")
                }
                append(" and ")
                withStyle(SpanStyle(color = Emerald, fontWeight = FontWeight.Bold)) {
                    append("Privacy Policy")
                }
                append(".")
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Already have an account?", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            TextButton(onClick = onLoginClick) {
                Text("Login", color = Emerald, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * v3.2.2 email verification (verify-email.png, SIMPLIFIED): envelope
 * vector icon with a check badge, "Check your email", "We sent a
 * verification link to <email>", "Open Email App", "Resend email".
 * No extra explanatory text, no bullet list, no spam-folder note.
 * Resend respects Supabase's 60-second cooldown: the button is
 * disabled with a visible countdown ("Resend in 42s").
 */
@Composable
fun VerifyEmailScreen(
    viewModel: AuthViewModel,
    email: String,
    onBackToLogin: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val colors = pingChatColors
    // The confirmation email was just sent, so the 60s cooldown starts
    // counted down from the moment this screen appears.
    var resendCooldown by remember { mutableStateOf(60) }
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000)
            resendCooldown--
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
        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier.size(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 132.dp, height = 96.dp)
                    .background(Emerald, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.MailOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .background(Color.White, CircleShape)
                    .border(3.dp, Emerald, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Emerald,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "Check your email",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "We sent a verification link to",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            textAlign = TextAlign.Center
        )
        Text(
            email,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        AuthMessage(state)
        Button(
            onClick = {
                // Open the user's email app; fall back to a mailto:
                // chooser when no default email app is set.
                val emailIntent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_APP_EMAIL)
                val toOpen =
                    if (emailIntent.resolveActivity(context.packageManager) != null) {
                        emailIntent
                    } else {
                        Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
                    }
                context.startActivity(toOpen)
            },
            shape = AuthFieldShape,
            colors = ButtonDefaults.buttonColors(containerColor = Emerald, contentColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Open Email App", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                viewModel.resendVerificationEmail(email)
                // Restart the cooldown whatever the server says — a
                // rate-limit refusal means a send already happened
                // within the last minute anyway.
                resendCooldown = 60
            },
            enabled = resendCooldown == 0,
            shape = AuthFieldShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend email",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBackToLogin) { Text("Back to log in") }
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
