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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import com.nohate.app.ui.theme.NoHateTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.text.style.TextOverflow

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
				title = { Text("NoHate", modifier = Modifier.clickable { nav.navigate("home") { popUpTo("home") { inclusive = false } } }) },
				colors = TopAppBarDefaults.topAppBarColors()
			)
		},
		snackbarHost = { SnackbarHost(snackbarHostState) },
		bottomBar = {
			if (route != "onboarding") {
				NavigationBar(tonalElevation = 0.dp) {
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
				onMessage = { msg: String -> scope.launch { snackbarHostState.showSnackbar(message = msg) } },
				onOpenManualTrain = { nav.navigate("manualTest") },
				onOpenReview = { nav.navigate("review") }
			) }
			composable("settings") { SettingsScreen(onOpenManualTest = { nav.navigate("manualTest") }, onMessage = { msg: String -> scope.launch { snackbarHostState.showSnackbar(message = msg) } }, onOpenOnboarding = { nav.navigate("onboarding") }) }
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
    val monitoredUrls = remember { mutableStateOf(store.getMonitoredUrls()) }
    val scanProgress = remember { mutableStateOf("") }
    val scanTotal = remember { mutableStateOf(store.getScanProgressTotal()) }
    val scanDone = remember { mutableStateOf(store.getScanProgressDone()) }
    val scanMsg = remember { mutableStateOf(store.getScanProgressMsg()) }
    var sessionActive by remember { mutableStateOf(store.getSessionCookies("instagram") != null) }

	LaunchedEffect(Unit) {
		flagged = store.getFlaggedItems()
		lastScanAt = store.getLastScanAt()
		lastScanTotal = store.getLastScanTotal()
		lastScanFlagged = store.getLastScanFlagged()
        monitoredUrls.value = store.getMonitoredUrls()
	}

    LaunchedEffect(true) {
        while (true) {
            val at = store.getLastScanAt()
            val tot = store.getLastScanTotal()
            val flg = store.getLastScanFlagged()
            if (at != lastScanAt || tot != lastScanTotal || flg != lastScanFlagged) {
                lastScanAt = at
                lastScanTotal = tot
                lastScanFlagged = flg
                flagged = store.getFlaggedItems()
            }
            val recent = store.getLogs().takeLast(5)
                .map { it.substringAfter(":", it).trim() }
                .filter { it.startsWith("scan:") || it.startsWith("provider:") }
                .takeLast(3)
            scanProgress.value = if (recent.isNotEmpty()) recent.joinToString(" \u2022 ") else ""
            // progress values
            scanTotal.value = store.getScanProgressTotal()
            scanDone.value = store.getScanProgressDone()
            scanMsg.value = store.getScanProgressMsg()
            sessionActive = store.getSessionCookies("instagram") != null
            delay(1000)
        }
    }

	LazyVerticalGrid(
		columns = GridCells.Fixed(2),
		contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 64.dp),
		modifier = Modifier.fillMaxSize(),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		// Live stats (moved to top)
		item(span = { GridItemSpan(2) }) {
			ElevatedCard(modifier = Modifier.fillMaxWidth()) {
				Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text("Live stats", style = MaterialTheme.typography.titleMedium)
					val whenStr = if (lastScanAt == 0L) "never" else DateFormat.getDateTimeInstance().format(Date(lastScanAt))
					Text("Last scan: ${whenStr}")
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						androidx.compose.material3.AssistChip(onClick = {}, label = { Text("Scanned ${lastScanTotal}") })
						androidx.compose.material3.AssistChip(onClick = {}, label = { Text("Flagged ${lastScanFlagged}") })
						androidx.compose.material3.AssistChip(onClick = {}, label = { Text("LLM ${if (llmEnabled.value) "on" else "off"}") })
                        androidx.compose.material3.AssistChip(onClick = {}, label = { Text(if (sessionActive) "Session on" else "Session off") })
					}
					if (scanTotal.value > 0) {
						LinearProgressIndicator(progress = (scanDone.value.coerceAtMost(scanTotal.value)).toFloat() / scanTotal.value.toFloat())
						Text("${scanMsg.value} (${scanDone.value}/${scanTotal.value})")
					} else {
						Text("Idle")
					}
				}
			}
		}

		// Monitored posts (moved up to align with Quick Actions)
		item(span = { GridItemSpan(2) }) {
			ElevatedCard(modifier = Modifier.fillMaxWidth()) {
				Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
					Text("Monitored posts", style = MaterialTheme.typography.titleMedium)
					if (monitoredUrls.value.isEmpty()) {
						Text("No monitored posts. Add a public or owned post URL to monitor during each scan.")
					} else {
						monitoredUrls.value.forEachIndexed { idx, url ->
							Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
								Text(url, modifier = Modifier.weight(1f))
								Button(onClick = {
									store.removeMonitoredUrlAt(idx)
									monitoredUrls.value = store.getMonitoredUrls()
									onMessage("Removed monitored URL")
								}) { Text("Remove") }
							}
						}
						FilledTonalButton(onClick = {
							store.clearMonitoredUrls()
							monitoredUrls.value = store.getMonitoredUrls()
						}) { Text("Clear all") }
					}
					var newUrl by remember { mutableStateOf("") }
					OutlinedTextField(value = newUrl, onValueChange = { newUrl = it }, label = { Text("Add URL") })
					FilledTonalButton(onClick = {
						val u = newUrl.trim()
						if (u.isNotEmpty()) {
							store.addMonitoredUrl(u)
							monitoredUrls.value = store.getMonitoredUrls()
							newUrl = ""
							onMessage("Added to monitor list")
						}
					}) { Text("Add post to monitor") }
				}
			}
		}

		// Minimal top actions
		item(span = { GridItemSpan(2) }) {
			ElevatedCard(modifier = Modifier.fillMaxWidth()) {
				Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
					Text("Quick Actions", style = MaterialTheme.typography.titleMedium)
					val isMonitoring = remember { mutableStateOf(false) }
					Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						androidx.compose.material3.AssistChip(onClick = {
							val nowReq = OneTimeWorkRequestBuilder<ScanWorker>().build()
							WorkManager.getInstance(context).enqueue(nowReq)
							onMessage("Scan started")
						}, label = { Text("Scan now") })
						androidx.compose.material3.AssistChip(onClick = {
							if (!isMonitoring.value) {
								store.setIntervalMinutes(minutes)
								val request = PeriodicWorkRequestBuilder<ScanWorker>(minutes.toLong(), TimeUnit.MINUTES).build()
								WorkManager.getInstance(context).enqueueUniquePeriodicWork(
									"comment-scan",
									ExistingPeriodicWorkPolicy.UPDATE,
									request
								)
								isMonitoring.value = true
								onMessage("Monitoring every ${minutes} min")
							} else {
								WorkManager.getInstance(context).cancelUniqueWork("comment-scan")
								isMonitoring.value = false
								onMessage("Monitoring stopped")
							}
						}, label = { Text(if (isMonitoring.value) "Stop" else "Monitor ${minutes}m") })
						androidx.compose.material3.AssistChip(onClick = onOpenReview, label = { Text("Review") })
					}
				}
			}
		}

		// Removed Recent flagged tile (preview now shown inside Live stats)

		// Live scan merged into Live stats above

		// (duplicate Live stats card removed)

		// Monitored posts last
	}
}
