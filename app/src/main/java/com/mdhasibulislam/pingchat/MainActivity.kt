package com.mdhasibulislam.pingchat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.mdhasibulislam.pingchat.data.CallManager
import com.mdhasibulislam.pingchat.data.FirebaseConfig
import com.mdhasibulislam.pingchat.data.Prefs
import com.mdhasibulislam.pingchat.data.SessionManager
import com.mdhasibulislam.pingchat.ui.screens.ActiveCallScreen
import com.mdhasibulislam.pingchat.ui.screens.ChatScreen
import com.mdhasibulislam.pingchat.ui.screens.ForgotPasswordScreen
import com.mdhasibulislam.pingchat.ui.screens.IncomingCallDialog
import com.mdhasibulislam.pingchat.ui.screens.LoginScreen
import com.mdhasibulislam.pingchat.ui.screens.MainScreen
import com.mdhasibulislam.pingchat.ui.screens.RegisterScreen
import com.mdhasibulislam.pingchat.ui.screens.SettingsScreen
import com.mdhasibulislam.pingchat.ui.theme.PingChatTheme
import com.mdhasibulislam.pingchat.ui.viewmodel.AuthViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.CallViewModel
import com.mdhasibulislam.pingchat.ui.viewmodel.ProfileViewModel

class MainActivity : ComponentActivity() {

    private var googleSignInClient: GoogleSignInClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Native Google sign-in: the Google ID token is exchanged with
        // Firebase Auth (signInWithCredential). The OAuth Web client ID
        // comes from google-services.json at runtime — it is EMPTY right
        // now (Google provider not enabled in the console), so the Google
        // button shows a friendly message and email login works.
        val googleClientId = FirebaseConfig.googleWebClientId(this)
        if (googleClientId != null) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(googleClientId)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        }

        setContent {
            val prefs = remember { Prefs(applicationContext) }
            var darkTheme by remember { mutableStateOf(prefs.darkTheme) }
            val isLoggedIn by SessionManager.isLoggedIn.collectAsState()
            val navController = rememberNavController()

            LaunchedEffect(Unit) {
                SessionManager.start()
            }

            // Calls: watch Firestore for ringing calls addressed to me
            // when signed in (so incoming calls ring), stop on sign-out.
            val currentUser by SessionManager.user.collectAsState()
            LaunchedEffect(isLoggedIn, currentUser?.uid) {
                if (isLoggedIn == true && currentUser != null) {
                    CallManager.start(applicationContext, currentUser!!.uid, prefs)
                } else if (isLoggedIn == false) {
                    CallManager.stop()
                }
            }

            // Route the user to the right screen when the session state changes.
            LaunchedEffect(isLoggedIn) {
                when (isLoggedIn) {
                    true -> {
                        if (navController.currentDestination?.route == "auth") {
                            navController.navigate("main") {
                                popUpTo("auth") { inclusive = true }
                            }
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
                    NavHost(navController = navController, startDestination = "auth") {
                        composable("auth") {
                            val authViewModel: AuthViewModel = viewModel()
                            var authScreen by remember { mutableStateOf("login") }
                            val googleClick = {
                                if (googleSignInClient == null) {
                                    authViewModel.googleError(
                                        "Google sign-in is not turned on yet — enable Google " +
                                            "in the Firebase console (see README), " +
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
