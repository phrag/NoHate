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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.nohate.app.auth.SessionLoginActivity
import com.nohate.app.data.SecureStore

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val sessionEnabled = remember { mutableStateOf(false) }
	val graphEnabled = remember { mutableStateOf(false) }
	val minutes = remember { mutableStateOf(30) }

	Column(
		modifier = Modifier.fillMaxSize().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text("Welcome to NoHate", style = MaterialTheme.typography.titleLarge)
		Text("We scan comments on your posts for hate speech — entirely on your device.")
		Text("Choose a connector (you can change this later in Settings):")
		Button(onClick = {
			graphEnabled.value = !graphEnabled.value
			store.setFeatureEnabled("ig_graph", graphEnabled.value)
		}) { Text(if (graphEnabled.value) "✓ Instagram Business/Creator enabled" else "Enable Instagram Business/Creator") }
		Button(onClick = {
			sessionEnabled.value = !sessionEnabled.value
			store.setFeatureEnabled("ig_session", sessionEnabled.value)
			if (sessionEnabled.value) {
				context.startActivity(Intent(context, SessionLoginActivity::class.java))
			}
		}) { Text(if (sessionEnabled.value) "✓ Instagram Personal enabled" else "Enable Instagram Personal (session)") }

		Text("How often should we scan?")
		Text("Every ${minutes.value} minutes")
		Slider(value = minutes.value.toFloat(), onValueChange = {
			minutes.value = it.toInt().coerceIn(15, 120)
		}, valueRange = 15f..120f)

		Button(onClick = {
			store.setIntervalMinutes(minutes.value)
			store.setOnboardingComplete(true)
			onFinished()
		}) { Text("Finish setup") }
	}
}
