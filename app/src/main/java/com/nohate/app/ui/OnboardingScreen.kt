package com.nohate.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.nohate.app.auth.SessionLoginActivity
import com.nohate.app.data.SecureStore
import com.nohate.app.llm.LlamaEngine
import com.nohate.app.ml.TfliteClassifier
import com.nohate.app.llm.LlmDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val step = remember { mutableStateOf(0) }
	val sessionEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_session")) }
	val graphEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_graph")) }
	val minutes = remember { mutableStateOf(store.getIntervalMinutes()) }
	val useQuant = remember { mutableStateOf(store.isUseQuantizedModel()) }
	val useLlm = remember { mutableStateOf(store.isUseLlm()) }
	val downloadMsg = remember { mutableStateOf("") }
	val downloading = remember { mutableStateOf(false) }

	Column(
		modifier = Modifier.fillMaxSize().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text("Setup NoHate", style = MaterialTheme.typography.titleLarge)
		when (step.value) {
			0 -> {
				Text("Welcome! We'll help you set up scanning for hate speech in your comments.")
				Text("Nothing leaves your device. You can change all settings later.")
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					Button(onClick = { step.value = 1 }) { Text("Get started") }
					OutlinedButton(onClick = { store.setOnboardingComplete(true); onFinished() }) { Text("Skip setup") }
				}
			}
			1 -> {
				Text("Choose a connector")
				Text("Pick how we'll read your comments.")
				Button(onClick = {
					graphEnabled.value = !graphEnabled.value
					store.setFeatureEnabled("ig_graph", graphEnabled.value)
				}) { Text(if (graphEnabled.value) "✓ Instagram Business/Creator enabled" else "Enable Instagram Business/Creator") }
				Button(onClick = {
					sessionEnabled.value = !sessionEnabled.value
					store.setFeatureEnabled("ig_session", sessionEnabled.value)
					if (sessionEnabled.value) context.startActivity(Intent(context, SessionLoginActivity::class.java))
				}) { Text(if (sessionEnabled.value) "✓ Instagram Personal enabled" else "Enable Instagram Personal (session)") }
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					OutlinedButton(onClick = { step.value = 0 }) { Text("Back") }
					Button(onClick = { step.value = 2 }) { Text("Next") }
				}
			}
			2 -> {
				Text("On-device models")
				Text("Enable fast TFLite model and optional tiny LLM for borderline cases.")
				Text("Why LLM? It double-checks borderline comments to reduce false positives.")
				Text("LLM download size: ~210 MB. Stored on-device. No data leaves your phone.")
				Text("Tip: Download on Wi‑Fi. Used only during scans.")
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					Button(onClick = {
						useQuant.value = !useQuant.value
						store.setUseQuantizedModel(useQuant.value)
						if (useQuant.value) try { TfliteClassifier(context).classify("warmup") } catch (_: Throwable) {}
					}) { Text(if (useQuant.value) "✓ Fast model enabled" else "Enable fast model") }
					Button(onClick = {
						useLlm.value = !useLlm.value
						store.setUseLlm(useLlm.value)
					}) { Text(if (useLlm.value) "✓ Tiny LLM enabled" else "Enable tiny LLM (optional)") }
				}
				val present = LlamaEngine(context).modelPresent()
				Text(if (present) "LLM model present" else "LLM model not downloaded")
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					Button(onClick = {
						if (downloading.value) return@Button
						downloading.value = true
						downloadMsg.value = "Downloading TinyLlama (~210 MB, Wi‑Fi recommended)..."
						CoroutineScope(Dispatchers.IO).launch {
							val ok = LlmDownloader.resolveAndDownload(
								context,
								repoId = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
								quantSuffix = "Q4_K_M"
							)
							downloading.value = false
							downloadMsg.value = if (ok) "LLM model downloaded" else "LLM download failed"
							store.appendLog(if (ok) "llm:download ok" else "llm:download fail")
						}
					}, enabled = !downloading.value) { Text("Download TinyLlama (~210 MB)") }
					OutlinedButton(onClick = { downloadMsg.value = if (LlamaEngine(context).modelPresent()) "Model present" else "Model missing" }) { Text("Check model") }
				}
				if (downloadMsg.value.isNotEmpty()) Text(downloadMsg.value)
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					OutlinedButton(onClick = { step.value = 1 }) { Text("Back") }
					Button(onClick = { step.value = 3 }) { Text("Next") }
				}
			}
			3 -> {
				Text("How often to scan?")
				Text("Every ${minutes.value} minutes")
				Slider(value = minutes.value.toFloat(), onValueChange = {
					minutes.value = it.toInt().coerceIn(15, 120)
				}, valueRange = 15f..120f)
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					OutlinedButton(onClick = { step.value = 2 }) { Text("Back") }
					Button(onClick = {
						store.setIntervalMinutes(minutes.value)
						store.setOnboardingComplete(true)
						onFinished()
					}) { Text("Finish setup") }
				}
			}
		}
	}
}
