package com.nohate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nohate.app.data.SecureStore
import com.nohate.app.work.ScanWorker
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.work.OneTimeWorkRequestBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nohate.app.ui.OnboardingScreen
import com.nohate.app.ui.SettingsScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nohate.app.ui.ManualTestScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.clickable
import com.nohate.app.ui.ConsoleScreen
import com.nohate.app.ui.ReviewScreen
import com.nohate.app.ui.MetricsCard
import android.content.Intent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import android.net.Uri

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
	val route = backEntry?.destination?.route
	val trainingItem = remember { mutableStateOf(store.peekTraining()) }

	LaunchedEffect(route) {
		// Poll for training items while app is visible
		while (route != null) {
			val next = store.peekTraining()
			if (next != trainingItem.value) trainingItem.value = next
			delay(1000)
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("NoHate", modifier = Modifier.clickable { nav.navigate("home") { popUpTo("home") { inclusive = false } } }) }
			)
		},
		snackbarHost = { SnackbarHost(snackbarHostState) },
		bottomBar = {
			if (route != "onboarding") {
				NavigationBar {
					NavigationBarItem(selected = route == "home", onClick = { nav.navigate("home") { launchSingleTop = true; restoreState = true } }, icon = { Icon(Icons.Filled.Home, contentDescription = "Home") }, label = { Text("Home") })
					NavigationBarItem(selected = route == "review", onClick = { nav.navigate("review") { launchSingleTop = true; restoreState = true } }, icon = { Icon(Icons.Filled.List, contentDescription = "Review") }, label = { Text("Review") })
					NavigationBarItem(selected = route == "manualTest", onClick = { nav.navigate("manualTest") { launchSingleTop = true; restoreState = true } }, icon = { Icon(Icons.Filled.School, contentDescription = "Train") }, label = { Text("Train") })
					NavigationBarItem(selected = route == "console", onClick = { nav.navigate("console") { launchSingleTop = true; restoreState = true } }, icon = { Icon(Icons.Filled.Info, contentDescription = "Console") }, label = { Text("Console") })
					NavigationBarItem(selected = route == "settings", onClick = { nav.navigate("settings") { launchSingleTop = true; restoreState = true } }, icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") }, label = { Text("Settings") })
				}
			}
		}
	) { padding ->
		NavHost(navController = nav, startDestination = startDest, modifier = Modifier.padding(padding)) {
			composable("onboarding") { OnboardingScreen { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } } }
			composable("home") { MainScreen(
				onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
				onOpenManualTrain = { nav.navigate("manualTest") },
				onOpenReview = { nav.navigate("review") }
			) }
			composable("settings") { SettingsScreen(onOpenManualTest = { nav.navigate("manualTest") }, onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }, onOpenOnboarding = { nav.navigate("onboarding") }) }
			composable("manualTest") { ManualTestScreen(onOpenReview = { nav.navigate("review") }) }
			composable("console") { ConsoleScreen() }
			composable("review") { ReviewScreen() }
		}
	}

	if (trainingItem.value != null) {
		AlertDialog(
			onDismissRequest = { /* keep until user answers; allow later to skip */ },
			title = { Text("Help improve NoHate") },
			text = { Text(trainingItem.value ?: "") },
			confirmButton = {
				TextButton(onClick = {
					val text = store.dequeueTraining() ?: return@TextButton
					store.addUserHatePhrase(text)
					store.appendLog("train:hate '${text.take(30)}'")
					trainingItem.value = store.peekTraining()
				}) { Text("Mark as hate") }
			},
			dismissButton = {
				TextButton(onClick = {
					val text = store.dequeueTraining() ?: return@TextButton
					store.addUserSafePhrase(text)
					store.appendLog("train:safe '${text.take(30)}'")
					trainingItem.value = store.peekTraining()
				}) { Text("Not hate") }
			}
		)
	}
}

@Composable
private fun MainScreen(onMessage: (String) -> Unit, onOpenManualTrain: () -> Unit, onOpenReview: () -> Unit) {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	var minutes by remember { mutableStateOf(store.getIntervalMinutes()) }
	var flagged by remember { mutableStateOf(store.getFlaggedItems()) }
	val clipboard = LocalClipboardManager.current
	val graphEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_graph")) }
	val sessionEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_session")) }
	val llmEnabled = remember { mutableStateOf(store.isUseLlm()) }
	var lastScanAt by remember { mutableStateOf(store.getLastScanAt()) }
	var lastScanTotal by remember { mutableStateOf(store.getLastScanTotal()) }
	var lastScanFlagged by remember { mutableStateOf(store.getLastScanFlagged()) }

	LaunchedEffect(Unit) {
		flagged = store.getFlaggedItems()
		lastScanAt = store.getLastScanAt()
		lastScanTotal = store.getLastScanTotal()
		lastScanFlagged = store.getLastScanFlagged()
	}

	LazyColumn(
		modifier = Modifier.fillMaxSize().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		item {
			ElevatedCard {
				Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text("Status", style = MaterialTheme.typography.titleMedium)
					Text("Connectors — Business/Creator: ${graphEnabled.value}, Personal: ${sessionEnabled.value}")
					Text("LLM enabled: ${llmEnabled.value}")
					val whenStr = if (lastScanAt == 0L) "never" else DateFormat.getDateTimeInstance().format(Date(lastScanAt))
					Text("Last scan: ${whenStr} (total ${lastScanTotal}, flagged ${lastScanFlagged})")
				}
			}
		}
		item { MetricsCard() }
		item {
			ElevatedCard {
				Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

					Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						Button(onClick = onOpenManualTrain) { Text("Local AI Training") }
						Button(onClick = onOpenReview) { Text("Review flagged") }
					}
				}
			}
		}
		item {
			ElevatedCard {
				Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
					Text("Flagged comments", style = MaterialTheme.typography.titleMedium)
					if (flagged.isEmpty()) {
						Text("No flagged comments yet. Try running a manual scan or importing from a public URL via Local AI Training.")
					} else {
						flagged.forEachIndexed { idx, item ->
							Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
								Text("• ${item.text}")
								Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
									IconButton(onClick = {
										clipboard.setText(AnnotatedString(item.text))
										onMessage("Copied to clipboard")
									}) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }
									IconButton(onClick = {
										val share = Intent(Intent.ACTION_SEND).apply {
											type = "text/plain"
											putExtra(Intent.EXTRA_TEXT, item.text)
										}
										context.startActivity(Intent.createChooser(share, "Share comment"))
									}) { Icon(Icons.Filled.Share, contentDescription = "Share") }
									item.sourceUrl?.let { url ->
										IconButton(onClick = {
											val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
											context.startActivity(intent)
										}) { Icon(Icons.Filled.Info, contentDescription = "Open") }
									}
									IconButton(onClick = {
										store.correctFalsePositive(idx)
										flagged = store.getFlaggedItems()
										onMessage("Marked as not hate")
									}) { Icon(Icons.Filled.CheckCircle, contentDescription = "Not hate") }
									IconButton(onClick = {
										store.hideFlaggedItemAt(idx)
										flagged = store.getFlaggedItems()
										onMessage("Hidden from list")
									}) { Icon(Icons.Filled.VisibilityOff, contentDescription = "Hide") }
									IconButton(onClick = {
										val report = Intent(Intent.ACTION_SEND).apply {
											type = "text/plain"
											putExtra(Intent.EXTRA_SUBJECT, "Report hate comment")
											putExtra(Intent.EXTRA_TEXT, "Reported comment:\n\n${item.text}\n\nSource: ${item.sourceUrl ?: "unknown"}")
										}
										context.startActivity(Intent.createChooser(report, "Report via"))
									}) { Icon(Icons.Filled.Flag, contentDescription = "Report") }
									IconButton(onClick = {
										store.removeFlaggedItemAt(idx)
										flagged = store.getFlaggedItems()
										onMessage("Removed")
									}) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
								}
							}
						}
					}
				}
			}
		}
	}
}
