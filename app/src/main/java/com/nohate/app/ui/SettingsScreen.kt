package com.nohate.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nohate.app.auth.SessionLoginActivity
import com.nohate.app.data.SecureStore

@Composable
fun SettingsScreen() {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val minutes = remember { mutableStateOf(store.getIntervalMinutes()) }
	val graphEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_graph")) }
	val sessionEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_session")) }

	Column(
		modifier = Modifier.fillMaxSize().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text("Settings", style = MaterialTheme.typography.titleLarge)

		Text("Scan every ${minutes.value} minutes")
		Slider(value = minutes.value.toFloat(), onValueChange = {
			minutes.value = it.toInt().coerceIn(15, 120)
		}, valueRange = 15f..120f)
		Button(onClick = { store.setIntervalMinutes(minutes.value) }) { Text("Save interval") }

		Text("Connectors")
		Button(onClick = {
			graphEnabled.value = !graphEnabled.value
			store.setFeatureEnabled("ig_graph", graphEnabled.value)
		}) { Text(if (graphEnabled.value) "Disable Instagram Business/Creator" else "Enable Instagram Business/Creator") }

		Button(onClick = {
			sessionEnabled.value = !sessionEnabled.value
			store.setFeatureEnabled("ig_session", sessionEnabled.value)
			if (sessionEnabled.value) {
				context.startActivity(Intent(context, SessionLoginActivity::class.java))
			}
		}) { Text(if (sessionEnabled.value) "Disable Instagram Personal" else "Enable Instagram Personal (session)") }

		Button(onClick = { store.clearProvider("instagram") }) { Text("Wipe Instagram credentials") }
	}
}
