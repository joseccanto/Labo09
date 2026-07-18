package com.labo05.demodata.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.labo05.demodata.DemoData
import com.labo05.demodata.ui.screens.*
import com.labo05.demodata.ui.viewmodel.GpsViewModel
import com.labo05.demodata.ui.viewmodel.GpsViewModelFactory
import com.labo05.demodata.ui.viewmodel.SessionViewModel

@Composable
fun Navigation() {
    val app = LocalContext.current.applicationContext as DemoData
    val sessionVm: SessionViewModel = viewModel(
        factory = SessionViewModel.Factory(app.sessionManager)
    )

    val rootNavController = rememberNavController()
    val isLoggedIn by sessionVm.isLoggedIn.collectAsStateWithLifecycle()

    // Navega reactivamente cada vez que isLoggedIn cambia
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            rootNavController.navigate("main") {
                popUpTo("auth") { inclusive = true }
            }
        } else {
            rootNavController.navigate("auth") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController    = rootNavController,
        startDestination = if (isLoggedIn) "main" else "auth"
    ) {
        // ── Grafo de Autenticación ──
        navigation(startDestination = "login", route = "auth") {
            composable("login") {
                LoginScreen(
                    onSubmit           = sessionVm::login,
                    onGoogleLogin = sessionVm::loginWithGoogle,
                    onRegisterNavigate = { rootNavController.navigate("register") }
                )
            }
            composable("register") {
                RegisterScreen(
                    onBack   = { rootNavController.popBackStack() },
                    onSubmit = { email, pass, onResult ->
                        sessionVm.register(email, pass) { success ->
                            onResult(success)
                            if (success) rootNavController.popBackStack()
                        }
                    }
                )
            }
        }

        // ── Pantalla Principal ──
        composable("main") {
            MainScaffold(sessionVm)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(sessionVm: SessionViewModel) {
    val nav      = rememberNavController()
    var selected by remember { mutableIntStateOf(0) }
    val username by sessionVm.username.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as DemoData

    val gpsViewModel: GpsViewModel = viewModel(
        factory = GpsViewModelFactory(app.gpsRepository)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title   = { Text("DemoData — ${username ?: "?"}") },
                actions = {
                    IconButton(onClick = { sessionVm.logout() }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val tabs = listOf(
                    "gps"     to (Icons.Default.LocationOn to "GNSS"),
                    "media"   to (Icons.Default.CameraAlt  to "Media"),
                    "audio"   to (Icons.Default.Mic        to "Audio"),
                    "profile" to (Icons.Default.Person     to "Perfil")
                )
                tabs.forEachIndexed { idx, (route, iconLabel) ->
                    val (icon, label) = iconLabel
                    NavigationBarItem(
                        selected = selected == idx,
                        onClick  = { selected = idx; nav.navigate(route) },
                        icon     = { Icon(icon, contentDescription = null) },
                        label    = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController    = nav,
            startDestination = "gps",
            modifier         = Modifier.padding(padding)
        ) {
            composable("gps") {
                GpsScreen(viewModel = gpsViewModel)
            }
            composable("media")   { MediaScreen() }
            composable("audio")   { AudioScreen() }
            composable("profile") { ProfileScreen(onLogout = sessionVm::logout, username = username) }
        }
    }
}