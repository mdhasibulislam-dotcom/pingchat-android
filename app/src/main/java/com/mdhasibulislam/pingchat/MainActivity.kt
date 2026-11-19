package com.mdhasibulislam.pingchat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.mdhasibulislam.pingchat.data.CallManager
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.Supabase
import com.mdhasibulislam.pingchat.ui.screens.ActiveCallScreen
import com.mdhasibulislam.pingchat.ui.screens.ChatScreen
import com.mdhasibulislam.pingchat.ui.screens.ForgotPasswordScreen
import com.mdhasibulislam.pingchat.ui.screens.IncomingCallDialog
import com.mdhasibulislam.pingchat.ui.screens.LoginScreen
import com.mdhasibulislam.pingchat.ui.screens.MainScreen
import com.mdhasibulislam.pingchat.ui.screens.RegisterScreen
import com.mdhasibulislam.pingchat.ui.screens.SettingsScreen
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.PingChatTheme
import com.mdhasibulislam.pingchat.ui.viewmodel.AuthViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.CallViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel

class MainActivity : ComponentActivity() {

    private var googleSignInClient: GoogleSignInClient? = null

    // Chat to open from a notification tap / Open chat action (v2.0.8).
    // Set from the launch intent (cold start) or onNewIntent (app already
    // running); drained by the navigation effects below once handled.
    private val openChatIdState = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("conversationId")?.let { openChatIdState.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notification tap / Open chat action carries the chat to open.
        intent.getStringExtra("conversationId")?.let { openChatIdState.value = it }

        // Start the session BEFORE the first frame: GoTrue restores the
        // saved session asynchronously (isLoggedIn starts null and flips
        // when the session loads), so the app shows the splash until it
        // knows whether to open chats or the login screen (no login flash).
        SessionManager.start()

        // Native Google sign-in: the Google ID token is exchanged with
        // Supabase Auth (signInWith(IDToken)). The OAuth Web client ID is
        // the constant in SupabaseClient.kt (the same Web client that is
        // configured in the Supabase console for the new project).
        val googleClientId = Supabase.GOOGLE_WEB_CLIENT_ID
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(googleClientId)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            val prefs = remember { Prefs(applicationContext) }
            var darkTheme by remember { mutableStateOf(prefs.darkTheme) }
            val isLoggedIn by SessionManager.isLoggedIn.collectAsState()
            val navController = rememberNavController()

            LaunchedEffect(Unit) {
                SessionManager.start()
            }
            // Calls: watch Realtime Broadcast for ringing calls addressed to me
            // when signed in (so incoming calls ring), stop on sign-out.
            val currentUser by SessionManager.user.collectAsState()
            LaunchedEffect(isLoggedIn, currentUser?.uid) {
                if (isLoggedIn == true && currentUser != null) {
                    CallManager.start(applicationContext, currentUser!!.uid, prefs)
                } else if (isLoggedIn == false) {
                    CallManager.stop()
                }
            }

            // v2.0.9: ask once for the notification permission (Android 13+)
            // after sign-in — without it, no notification shows at all on a
            // fresh install. The pushPrompted flag makes sure we never nag.
            val pushPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* asked once — granted or not, we do not ask again */ }
            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn == true && !prefs.pushPrompted &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        applicationContext, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    prefs.pushPrompted = true
                    pushPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Route the user to the right screen when the session state changes.
            LaunchedEffect(isLoggedIn) {
                // The NavHost is only composed once the session has resolved
                // (isLoggedIn != null), and it sets the graph in its own
                // LaunchedEffect — which can run AFTER this one. Until the
                // graph is set, navigate() throws IllegalArgumentException
                // (v3.0.0 launch crash). currentDestination is null until
                // the graph exists, so skip: the NavHost's startDestination
                // already shows the right first screen ("main" / "auth").
                if (navController.currentDestination == null) return@LaunchedEffect
                when (isLoggedIn) {
                    true -> {
                        if (navController.currentDestination?.route == "auth") {
                            navController.navigate("main") {
                                popUpTo("auth") { inclusive = true }
                            }
                        }
                        // Notification tap / Open chat action: open that chat
                        // once the main screen is up (v2.0.8).
                        val chatId = openChatIdState.value
                        if (chatId != null) {
                            openChatIdState.value = null
                            navController.navigate("chat/$chatId")
                        }
                    }
                    false -> {
                        if (navController.currentDestination?.route != "auth") {
                            navController.navigate("auth") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                    null -> { /* still loading the saved session */ }
                }
            }
            // Notification tap / Open chat action while the app is already
            // running (v2.0.8): onNewIntent sets openChatIdState, this
            // opens that chat.
            val openChatId by openChatIdState
            LaunchedEffect(openChatId) {
                if (isLoggedIn == true && openChatId != null &&
                    navController.currentDestination != null &&
                    navController.currentDestination?.route != "chat/$openChatId"
                ) {
                    navController.navigate("chat/$openChatId")
                    openChatIdState.value = null
                }
            }

            PingChatTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Call overlays sit above every screen (chats, tabs,
                    // auth): an incoming call must ring even while the
                    // user is inside a chat, and the in-call screen must
                    // cover the tab bar.
                    val callViewModel: CallViewModel = viewModel()
                    val incomingCall by callViewModel.incoming.collectAsState()
                    val activeCall by callViewModel.session.collectAsState()
                    val callError by callViewModel.error.collectAsState()
                    LaunchedEffect(callError) {
                        if (callError != null) {
                            Toast.makeText(applicationContext, callError, Toast.LENGTH_SHORT).show()
                            callViewModel.clearError()
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                    if (isLoggedIn == null) {
                        // Splash while the saved session loads — the app
                        // never flashes the login screen first.
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "PingChat",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald
                                )
                                Spacer(Modifier.height(16.dp))
                                CircularProgressIndicator(color = Emerald)
                            }
                        }
                    } else {
                    NavHost(navController = navController, startDestination = if (isLoggedIn == true) "main" else "auth") {
                        composable("auth") {
                            val authViewModel: AuthViewModel = viewModel()
                            var authScreen by remember { mutableStateOf("login") }
                            val googleClick = {
                                if (googleSignInClient == null) {
                                    authViewModel.googleError(
                                        "Google sign-in is not turned on yet — enable Google " +
                                            "in the Supabase console (see README), " +
                                            "or use email login."
                                    )
                                } else {
                                    val intent = googleSignInClient!!.signInIntent
                                    @Suppress("DEPRECATION")
                                    startActivityForResult(intent, RC_GOOGLE_SIGN_IN)
                                }
                            }
                            // Google sign-in result hand-off from onActivityResult.
                            LaunchedEffect(Unit) {
                                AuthViewModel.pendingGoogleToken.collect { token ->
                                    if (token != null) {
                                        AuthViewModel.pendingGoogleToken.value = null
                                        authViewModel.googleSignIn(token)
                                    }
                                }
                            }
                            LaunchedEffect(Unit) {
                                AuthViewModel.pendingGoogleError.collect { message ->
                                    if (message != null) {
                                        AuthViewModel.pendingGoogleError.value = null
                                        authViewModel.googleError(message)
                                    }
                                }
                            }
                            when (authScreen) {
                                "register" -> RegisterScreen(
                                    viewModel = authViewModel,
                                    onGoogleClick = googleClick,
                                    onLoginClick = { authScreen = "login" }
                                )
                                "forgot" -> ForgotPasswordScreen(
                                    viewModel = authViewModel,
                                    onBackToLogin = { authScreen = "login" }
                                )
                                else -> LoginScreen(
                                    viewModel = authViewModel,
                                    onGoogleClick = googleClick,
                                    onRegisterClick = { authScreen = "register" },
                                    onForgotClick = { authScreen = "forgot" }
                                )
                            }
                        }
                        composable("main") {
                            MainScreen(
                                onOpenChat = { id -> navController.navigate("chat/$id") },
                                onSettingsClick = { navController.navigate("settings") },
                                darkTheme = darkTheme,
                                onDarkThemeChange = { enabled ->
                                    darkTheme = enabled
                                    prefs.darkTheme = enabled
                                },
                                prefs = prefs
                            )
                        }
                        composable("settings") {
                            val profileViewModel: ProfileViewModel = viewModel()
                            SettingsScreen(
                                viewModel = profileViewModel,
                                prefs = prefs,
                                darkTheme = darkTheme,
                                onDarkThemeChange = { enabled ->
                                    darkTheme = enabled
                                    prefs.darkTheme = enabled
                                }
                            )
                        }
                        composable("chat/{conversationId}") { backStackEntry ->
                            val conversationId =
                                backStackEntry.arguments?.getString("conversationId") ?: ""
                            ChatScreen(
                                conversationId = conversationId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    }
                        // Call overlays above every screen.
                        if (incomingCall != null) {
                            IncomingCallDialog(incoming = incomingCall!!, viewModel = callViewModel)
                        }
                        if (activeCall != null) {
                            ActiveCallScreen(session = activeCall!!, viewModel = callViewModel)
                        }
                    }
                }
            }
        }
    }

    @Deprecated("Used for the Google sign-in result (kept simple for Phase 1).")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RC_GOOGLE_SIGN_IN) return
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                AuthViewModel.pendingGoogleToken.value = idToken
            } else {
                AuthViewModel.pendingGoogleError.value =
                    "Google did not return an ID token. Try again or use email login."
            }
        } catch (e: Exception) {
            AuthViewModel.pendingGoogleError.value =
                "Google sign-in failed: ${e.localizedMessage ?: "unknown error"}"
        }
    }

    companion object {
        private const val RC_GOOGLE_SIGN_IN = 9001
    }
}
