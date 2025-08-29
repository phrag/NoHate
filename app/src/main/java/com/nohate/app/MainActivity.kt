package com.nohate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.ManageAccounts
import com.nohate.app.ui.AccountsScreen

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

@Composable
private fun App() {
	var showAccounts by remember { mutableStateOf(false) }
	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("NoHate") },
				actions = {
					IconButton(onClick = { showAccounts = !showAccounts }) {
						Icon(Icons.Filled.ManageAccounts, contentDescription = "Accounts")
					}
				}
			)
		}
	) { padding ->
		if (showAccounts) {
			AccountsScreen()
		} else {
			MainScreen()
		}
	}
}

@Composable
private fun MainScreen() {
	val context = androidx.compose.ui.platform.LocalContext.current
	val store = remember { SecureStore(context) }
	var minutes by remember { mutableStateOf(store.getIntervalMinutes()) }
	var flagged by remember { mutableStateOf(store.getFlaggedComments()) }

	LaunchedEffect(Unit) {
		flagged = store.getFlaggedComments()
	}

	Column(
		modifier = Modifier.fillMaxSize(),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text(text = "Interval: ${minutes} min")
		Slider(
			value = minutes.toFloat(),
			onValueChange = {
				minutes = it.toInt().coerceIn(5, 120)
			},
			valueRange = 5f..120f
		)
		Button(onClick = {
			store.setIntervalMinutes(minutes)
			val request = PeriodicWorkRequestBuilder<ScanWorker>(minutes.toLong(), TimeUnit.MINUTES).build()
			WorkManager.getInstance(context).enqueueUniquePeriodicWork(
				"comment-scan",
				ExistingPeriodicWorkPolicy.UPDATE,
				request
			)
		}) {
			Text("Start scanning")
		}

		Text("Flagged comments:")
		LazyColumn(contentPadding = PaddingValues(8.dp)) {
			items(flagged) { c ->
				Text("• ${c}")
			}
		}
	}
}
