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
import com.mdhasibulislam.pingchat.data.PingChatMessagingService
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.data.Supabase
import com.mdhasibulislam.pingchat.ui.screens.ActiveCallScreen
import com.mdhasibulislam.pingchat.ui.screens.AppearanceScreen
import com.mdhasibulislam.pingchat.ui.screens.ChangePasswordScreen
import com.mdhasibulislam.pingchat.ui.screens.ChatScreen
import com.mdhasibulislam.pingchat.ui.screens.EditProfileScreen
import com.mdhasibulislam.pingchat.ui.screens.ForgotPasswordScreen
import com.mdhasibulislam.pingchat.ui.screens.IncomingCallDialog
import com.mdhasibulislam.pingchat.ui.screens.LoginScreen
import com.mdhasibulislam.pingchat.ui.screens.MainScreen
import com.mdhasibulislam.pingchat.ui.screens.MfaChallengeScreen
import com.mdhasibulislam.pingchat.ui.screens.NotificationsScreen
import com.mdhasibulislam.pingchat.ui.screens.PrivacyPolicyScreen
import com.mdhasibulislam.pingchat.ui.screens.PrivacySecurityScreen
import com.mdhasibulislam.pingchat.ui.screens.RegisterScreen
import com.mdhasibulislam.pingchat.ui.screens.SettingsScreen
import com.mdhasibulislam.pingchat.ui.screens.TwoFactorScreen
import com.mdhasibulislam.pingchat.ui.theme.Emerald
import com.mdhasibulislam.pingchat.ui.theme.PingChatTheme
import com.mdhasibulislam.pingchat.ui.theme.accentColorFor
import com.mdhasibulislam.pingchat.ui.viewmodel.AuthViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.CallViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel

class MainActivity : ComponentActivity() {

    private var googleSignInClient: GoogleSignInClient? = null

    // Chat to open from a notification tap / Open chat action (v2.0.8).
    // Set from the launch intent (cold start) or onNewIntent (app already
    // running); drained by the navigation effects below once handled.
    private val openChatIdState = mutableStateOf<String?>(null)

    // Friend-request notification tap (v3.0.2): open the People tab, where
    // pending requests sit at the top. Same cold-start / onNewIntent
    // pattern as openChatIdState.
    private val openFriendRequestsState = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("conversationId")?.let { openChatIdState.value = it }
        if (intent.getBooleanExtra(PingChatMessagingService.EXTRA_FRIEND_REQUEST, false)) {
            openFriendRequestsState.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notification tap / Open chat action carries the chat to open.
        intent.getStringExtra("conversationId")?.let { openChatIdState.value = it }

        // Friend-request notification tap carries no chat — it opens People.
        if (intent.getBooleanExtra(PingChatMessagingService.EXTRA_FRIEND_REQUEST, false)) {
            openFriendRequestsState.value = true
        }

        // Start the session BEFORE the first frame: GoTrue restores the
        // saved session asynchronously (isLoggedIn starts null and flips
        // when the session loads), so the app shows the splash until it
        // knows whether to open chats or the login screen (no login flash).
        // v3.0.2: pass the context so SessionManager can read Prefs
        // (notifications toggle) and not re-save the FCM token while OFF.
        SessionManager.start(applicationContext)

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
            // v3.2.0 Two-factor: while this is true the user is signed in
            // but only AAL1 — the code prompt, not the chats.
            val mfaPending by SessionManager.mfaChallengePending.collectAsState()
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
            LaunchedEffect(isLoggedIn, mfaPending) {
                if (isLoggedIn == true && !mfaPending && !prefs.pushPrompted &&
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
            // v3.2.0 Two-factor: while the MFA challenge is pending, the
            // user goes to "mfa_challenge" instead of "main" — an AAL1
            // session never reaches the chats without its code.
            LaunchedEffect(isLoggedIn, mfaPending) {
                // The NavHost is only composed once the session has resolved
                // (isLoggedIn != null), and it sets the graph in its own
                // LaunchedEffect — which can run AFTER this one. Until the
                // graph is set, navigate() throws IllegalArgumentException
                // (v3.0.0 launch crash). currentDestination is null until
                // the graph exists, so skip: the NavHost's startDestination
                // already shows the right first screen ("main" / "auth" /
                // "mfa_challenge").
                if (navController.currentDestination == null) return@LaunchedEffect
                when {
                    isLoggedIn == true && mfaPending -> {
                        if (navController.currentDestination?.route != "mfa_challenge") {
                            navController.navigate("mfa_challenge") {
                                popUpTo("auth") { inclusive = true }
                            }
                        }
                    }
                    isLoggedIn == true -> {
                        if (navController.currentDestination?.route == "auth" ||
                            navController.currentDestination?.route == "mfa_challenge"
                        ) {
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
                    isLoggedIn == false -> {
                        if (navController.currentDestination?.route != "auth") {
                            navController.navigate("auth") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                    else -> { /* still loading the saved session */ }
                }
            }
            // Notification tap / Open chat action while the app is already
            // running (v2.0.8): onNewIntent sets openChatIdState, this
            // opens that chat. v3.2.0 Two-factor: gated on !mfaPending —
            // without this, tapping a message notification while the
            // code prompt is showing would open the chat straight past
            // the MFA gate (the routing effect above opens the pending
            // chat after verification instead).
            val openChatId by openChatIdState
            val openFriendRequests by openFriendRequestsState
            LaunchedEffect(openChatId, mfaPending) {
                if (isLoggedIn == true && !mfaPending && openChatId != null &&
                    navController.currentDestination != null &&
                    navController.currentDestination?.route != "chat/$openChatId"
                ) {
                    navController.navigate("chat/$openChatId")
                    openChatIdState.value = null
                }
            }

            PingChatTheme(darkTheme = darkTheme, accent = accentColorFor(prefs.accentColor), fontScale = prefs.fontSize) {
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
                    NavHost(navController = navController, startDestination = if (isLoggedIn == true) (if (mfaPending) "mfa_challenge" else "main") else "auth") {
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
                        // v3.2.0: Two-factor login challenge — the start
                        // destination (and the route) while the session is
                        // AAL1. Verifying flips mfaChallengePending off
                        // and the routing effect above takes the user to
                        // "main"; cancelling signs out (routes to "auth").
                        composable("mfa_challenge") {
                            MfaChallengeScreen()
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
                                prefs = prefs,
                                openPeopleTab = openFriendRequests,
                                onPeopleTabConsumed = { openFriendRequestsState.value = false },
                                // v3.1.0 (worker A): Settings > Privacy & Security.
                                onPrivacySecurityClick = { navController.navigate("privacy_security") },
                                onPrivacyPolicyClick = { navController.navigate("privacy_policy") },
                                // v3.2.0 (worker B): profile header opens Edit Profile.
                                onEditProfileClick = { navController.navigate("edit_profile") }
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
                                },
                                onPrivacySecurityClick = { navController.navigate("privacy_security") },
                                onPrivacyPolicyClick = { navController.navigate("privacy_policy") },
                                // v3.2.0 (worker B): Settings rows (worker C adds the params).
                                onEditProfileClick = { navController.navigate("edit_profile") },
                                onNotificationsClick = { navController.navigate("notifications") },
                                onAppearanceClick = { navController.navigate("appearance") }
                            )
                        }
                        // v3.1.0: Privacy & Security (worker A) — opened from
                        // Settings > Privacy & Security. The Privacy Policy
                        // row at its bottom opens "privacy_policy" (worker G).
                        // v3.2.0 (worker B): Change Password + Two-Factor
                        // Authentication open from this screen's rows.
                        composable("privacy_security") {
                            PrivacySecurityScreen(
                                onBack = { navController.popBackStack() },
                                onOpenPrivacyPolicy = { navController.navigate("privacy_policy") },
                                onChangePasswordClick = { navController.navigate("change_password") },
                                onTwoFactorClick = { navController.navigate("two_factor") }
                            )
                        }
                        // v3.2.0 (worker B): Change Password (mockup screen 4).
                        composable("change_password") {
                            ChangePasswordScreen(onBack = { navController.popBackStack() })
                        }
                        // v3.2.0 (worker B): Two-Factor Authentication (real TOTP).
                        composable("two_factor") {
                            TwoFactorScreen(onBack = { navController.popBackStack() })
                        }
                        // v3.2.0 (worker A defines this screen): Edit Profile.
                        composable("edit_profile") {
                            val profileViewModel: ProfileViewModel = viewModel()
                            EditProfileScreen(
                                viewModel = profileViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        // v3.2.0 (worker C defines this screen): Notifications.
                        composable("notifications") {
                            val profileViewModel: ProfileViewModel = viewModel()
                            NotificationsScreen(
                                onBack = { navController.popBackStack() },
                                prefs = prefs,
                                viewModel = profileViewModel
                            )
                        }
                        // v3.2.0 (worker C defines this screen): Appearance.
                        composable("appearance") {
                            AppearanceScreen(
                                onBack = { navController.popBackStack() },
                                prefs = prefs,
                                darkTheme = darkTheme,
                                onDarkThemeChange = { enabled ->
                                    darkTheme = enabled
                                    prefs.darkTheme = enabled
                                }
                            )
                        }
                        // v3.1.0: Privacy Policy (worker G) — opened from
                        // Settings > Privacy & Security > "Privacy Policy".
                        composable("privacy_policy") {
                            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
                        }
                        composable("chat/{conversationId}") { backStackEntry ->
                            val conversationId =
                                backStackEntry.arguments?.getString("conversationId") ?: ""
                            ChatScreen(
                                conversationId = conversationId,
                                onBack = { navController.popBackStack() },
                                // v3.1.1: let "Message" from a nested public
                                // profile and a just-created group open that
                                // chat (ChatScreen's onOpenChat param).
                                onOpenChat = { id -> navController.navigate("chat/$id") },
                                // v3.2.0 (worker F): the peer profile's
                                // "Privacy & Security" row opens the global
                                // Privacy & Security screen.
                                onOpenPrivacySecurity = { navController.navigate("privacy_security") }
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
