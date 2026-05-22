package com.nohate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nohate.app.data.SecureStore
import com.nohate.app.ui.BenchScreen
import com.nohate.app.ui.ConsoleScreen
import com.nohate.app.ui.HomeScreen
import com.nohate.app.ui.ManualTestScreen
import com.nohate.app.ui.OnboardingScreen
import com.nohate.app.ui.ReviewScreen
import com.nohate.app.ui.SettingsScreen
import com.nohate.app.ui.theme.NoHateTheme
import com.nohate.app.work.ScanWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NoHateTheme { App() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SecureStore(context) }
    val nav = rememberNavController()
    val startDest = if (store.isOnboardingComplete()) "home" else "onboarding"
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route
    val trainingItem = remember { mutableStateOf(store.peekTraining()) }

    LaunchedEffect(route) {
        while (route != null) {
            val next = store.peekTraining()
            if (next != trainingItem.value) trainingItem.value = next
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "NoHate",
                        modifier = Modifier.clickable {
                            nav.navigate("home") { popUpTo("home") { inclusive = false } }
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (route != "onboarding") {
                NavigationBar(tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = route == "home",
                        onClick = { nav.navigate("home") { launchSingleTop = true; restoreState = true } },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = route == "review",
                        onClick = { nav.navigate("review") { launchSingleTop = true; restoreState = true } },
                        icon = { Icon(Icons.Filled.List, contentDescription = "Review") },
                        label = { Text("Review") },
                    )
                    NavigationBarItem(
                        selected = route == "manualTest",
                        onClick = { nav.navigate("manualTest") { launchSingleTop = true; restoreState = true } },
                        icon = { Icon(Icons.Filled.School, contentDescription = "Train") },
                        label = { Text("Train") },
                    )
                    NavigationBarItem(
                        selected = route == "console",
                        onClick = { nav.navigate("console") { launchSingleTop = true; restoreState = true } },
                        icon = { Icon(Icons.Filled.Info, contentDescription = "Console") },
                        label = { Text("Console") },
                    )
                    NavigationBarItem(
                        selected = route == "settings",
                        onClick = { nav.navigate("settings") { launchSingleTop = true; restoreState = true } },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startDest,
            modifier = Modifier.padding(padding),
        ) {
            composable("onboarding") {
                OnboardingScreen { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } }
            }
            composable("home") {
                HomeScreen(
                    onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                    onOpenReview = { nav.navigate("review") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    onOpenManualTest = { nav.navigate("manualTest") },
                    onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                    onOpenOnboarding = { nav.navigate("onboarding") },
                    onOpenBenchmark = { nav.navigate("bench") },
                )
            }
            composable("manualTest") { ManualTestScreen(onOpenReview = { nav.navigate("review") }) }
            composable("console") { ConsoleScreen() }
            composable("review") { ReviewScreen() }
            composable("bench") {
                BenchScreen(onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } })
            }
        }
    }

    if (trainingItem.value != null) {
        val skip = {
            store.dequeueTraining()
            trainingItem.value = store.peekTraining()
        }
        AlertDialog(
            onDismissRequest = { skip() },
            title = { Text("Was this hate speech?") },
            text = {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    Text("“${trainingItem.value ?: ""}”")
                    Text(
                        "Your answer trains the on-device classifier. Tap outside or Skip to decide later.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val text = store.dequeueTraining() ?: return@TextButton
                    store.addUserHatePhrase(text)
                    store.appendLog("train:hate '${text.take(30)}'")
                    trainingItem.value = store.peekTraining()
                }) { Text("Hate") }
            },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { skip() }) { Text("Skip") }
                    TextButton(onClick = {
                        val text = store.dequeueTraining() ?: return@TextButton
                        store.addUserSafePhrase(text)
                        store.appendLog("train:safe '${text.take(30)}'")
                        trainingItem.value = store.peekTraining()
                    }) { Text("Not hate") }
                }
            },
        )
    }
}
