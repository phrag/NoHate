package com.nohate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nohate.app.data.SecureStore
import kotlinx.coroutines.delay

@Composable
fun ConsoleScreen() {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val logs = remember { mutableStateOf(store.getLogs()) }

	LaunchedEffect(Unit) {
		while (true) {
			logs.value = store.getLogs()
			delay(1000)
		}
	}

	Column(
		modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Text("Console", style = MaterialTheme.typography.titleLarge)
		if (logs.value.isEmpty()) {
			Text("No logs yet.")
		} else {
			logs.value.forEach { line ->
				Text(line)
			}
		}
		Button(onClick = {
			store.clearLogs()
			logs.value = emptyList()
		}) { Text("Clear logs") }
	}
}
