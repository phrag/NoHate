package com.nohate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nohate.app.data.SecureStore
import com.nohate.app.work.ScanWorker
import java.util.concurrent.TimeUnit
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.work.OneTimeWorkRequestBuilder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nohate.app.ui.OnboardingScreen
import com.nohate.app.ui.SettingsScreen
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.nohate.app.ui.ManualTestScreen
import androidx.compose.material3.FloatingActionButton
import androidx.navigation.compose.currentBackStackEntryAsState

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			MaterialTheme {
				App()
			}
		}
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
	val isHome = backEntry?.destination?.route == "home"

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("NoHate") },
				actions = {
					IconButton(onClick = { nav.navigate("manualTest") }) {
						Icon(Icons.Filled.School, contentDescription = "Train AI")
					}
					IconButton(onClick = { nav.navigate("settings") }) {
						Icon(Icons.Filled.Settings, contentDescription = "Settings")
					}
				}
			)
		},
		snackbarHost = { SnackbarHost(snackbarHostState) },
		floatingActionButton = {
			if (isHome) {
				FloatingActionButton(onClick = { nav.navigate("manualTest") }) {
					Icon(Icons.Filled.School, contentDescription = "Train AI")
				}
			}
		}
	) { padding ->
		NavHost(navController = nav, startDestination = startDest, modifier = Modifier.padding(padding)) {
			composable("onboarding") { OnboardingScreen { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } } }
			composable("home") { MainScreen(
				onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
				onOpenManualTrain = { nav.navigate("manualTest") }
			) }
			composable("settings") { SettingsScreen(onOpenManualTest = { nav.navigate("manualTest") }) }
			composable("manualTest") { ManualTestScreen() }
		}
	}
}

@Composable
private fun MainScreen(onMessage: (String) -> Unit, onOpenManualTrain: () -> Unit) {
	val context = androidx.compose.ui.platform.LocalContext.current
	val store = remember { SecureStore(context) }
	var minutes by remember { mutableStateOf(store.getIntervalMinutes()) }
	var flagged by remember { mutableStateOf(store.getFlaggedComments()) }

	LaunchedEffect(Unit) {
		flagged = store.getFlaggedComments()
	}

	Column(
		modifier = Modifier.fillMaxSize().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text("Home", style = MaterialTheme.typography.titleLarge)
		Text("Scanning", style = MaterialTheme.typography.titleMedium)
		Text(text = "Every ${minutes} min")
		Slider(
			value = minutes.toFloat(),
			onValueChange = { minutes = it.toInt().coerceIn(15, 120) },
			valueRange = 15f..120f
		)
		Button(onClick = {
			store.setIntervalMinutes(minutes)
			val request = PeriodicWorkRequestBuilder<ScanWorker>(minutes.toLong(), TimeUnit.MINUTES).build()
			WorkManager.getInstance(context).enqueueUniquePeriodicWork(
				"comment-scan",
				ExistingPeriodicWorkPolicy.UPDATE,
				request
			)
			onMessage("Scheduled scanning every ${minutes} min")
		}) { Text("Start scanning") }

		Button(onClick = {
			val nowReq = OneTimeWorkRequestBuilder<ScanWorker>().build()
			WorkManager.getInstance(context).enqueue(nowReq)
			onMessage("Scan started")
		}) { Text("Run now") }

		Button(onClick = onOpenManualTrain) { Text("Local AI Training") }

		Text("Flagged comments", style = MaterialTheme.typography.titleMedium)
		if (flagged.isEmpty()) {
			Text("No flagged comments yet.")
		} else {
			LazyColumn(contentPadding = PaddingValues(8.dp)) {
				items(flagged) { c -> Text("• ${c}") }
			}
		}
	}
}
