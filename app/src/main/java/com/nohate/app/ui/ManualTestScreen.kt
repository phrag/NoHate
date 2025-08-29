package com.nohate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nohate.app.NativeClassifier
import androidx.compose.ui.platform.LocalContext
import com.nohate.app.data.SecureStore

@Composable
fun ManualTestScreen() {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val input = remember { mutableStateOf("") }
	val score = remember { mutableStateOf<Float?>(null) }
	val error = remember { mutableStateOf<String?>(null) }

	Column(
		modifier = Modifier.fillMaxSize().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text("Manual classifier test", style = MaterialTheme.typography.titleLarge)
		OutlinedTextField(
			value = input.value,
			onValueChange = { input.value = it },
			modifier = Modifier.fillMaxSize().weight(1f, fill = false),
			label = { Text("Enter text to classify") },
			minLines = 3
		)
		Button(onClick = {
			try {
				val hate = store.getUserHatePhrases()
				val safe = store.getUserSafePhrases()
				val s = NativeClassifier.classifyWithUser(input.value, hate, safe)
				score.value = s
				error.value = null
			} catch (t: Throwable) {
				error.value = t.message
			}
		}, enabled = input.value.isNotBlank()) {
			Text("Classify")
		}
		Button(onClick = {
			store.addUserHatePhrase(input.value)
			val s = NativeClassifier.classifyWithUser(input.value, store.getUserHatePhrases(), store.getUserSafePhrases())
			score.value = s
		}) { Text("Mark as hate (train)") }
		Button(onClick = {
			store.addUserSafePhrase(input.value)
			val s = NativeClassifier.classifyWithUser(input.value, store.getUserHatePhrases(), store.getUserSafePhrases())
			score.value = s
		}) { Text("Mark as not hate (train)") }

		score.value?.let {
			val flagged = it >= 0.8f
			Text("Score: ${"%.2f".format(it)} — ${if (flagged) "Flagged as hate" else "Not hate"}")
		}
		error.value?.let { Text("Error: ${it}") }
	}
}
