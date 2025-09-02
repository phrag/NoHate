package com.nohate.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nohate.app.auth.SessionLoginActivity
import com.nohate.app.data.SecureStore
import com.nohate.app.ml.TfliteClassifier
import com.nohate.app.llm.LlamaEngine
import com.nohate.app.llm.LlmDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nohate.app.work.ScanWorker

@Composable
fun SettingsScreen(onOpenManualTest: (() -> Unit)? = null, onMessage: ((String) -> Unit)? = null, onOpenOnboarding: (() -> Unit)? = null) {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val minutes = remember { mutableStateOf(store.getIntervalMinutes()) }
	val graphEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_graph")) }
	val sessionEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_session")) }
	val useQuant = remember { mutableStateOf(store.isUseQuantizedModel()) }
	val useLlm = remember { mutableStateOf(store.isUseLlm()) }
	val threshold = remember { mutableStateOf(store.getFlagThreshold()) }
	val modelPresent = remember { mutableStateOf(LlamaEngine(context).modelPresent()) }
	val showLlmPrompt = remember { mutableStateOf(false) }
	val downloading = remember { mutableStateOf(false) }
	val downloadMsg = remember { mutableStateOf("") }

	Column(
		modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text("Settings", style = MaterialTheme.typography.titleLarge)

		Button(onClick = { onOpenOnboarding?.invoke() }) { Text("Run setup wizard") }

		Text("Scan every ${minutes.value} minutes")
		Slider(value = minutes.value.toFloat(), onValueChange = {
			minutes.value = it.toInt().coerceIn(15, 120)
		}, valueRange = 15f..120f)
		Button(onClick = { store.setIntervalMinutes(minutes.value) }) { Text("Save interval") }

		Text("Flagging threshold: ${String.format("%.2f", threshold.value)}")
		Slider(value = threshold.value, onValueChange = {
			threshold.value = it.coerceIn(0.5f, 0.95f)
		}, valueRange = 0.5f..0.95f)
		Button(onClick = {
			store.setFlagThreshold(threshold.value)
			store.appendLog("settings:threshold ${String.format("%.2f", threshold.value)}")
			onMessage?.invoke("Threshold set to ${String.format("%.2f", threshold.value)}")
		}) { Text("Save threshold") }

		Text("Connectors")
		Button(onClick = {
			graphEnabled.value = !graphEnabled.value
			store.setFeatureEnabled("ig_graph", graphEnabled.value)
			store.appendLog("settings:ig_graph ${graphEnabled.value}")
		}) { Text(if (graphEnabled.value) "Disable Instagram Business/Creator" else "Enable Instagram Business/Creator") }

		Button(onClick = {
			sessionEnabled.value = !sessionEnabled.value
			store.setFeatureEnabled("ig_session", sessionEnabled.value)
			store.appendLog("settings:ig_session ${sessionEnabled.value}")
			if (sessionEnabled.value) {
				context.startActivity(Intent(context, SessionLoginActivity::class.java))
			}
		}) { Text(if (sessionEnabled.value) "Disable Instagram Personal" else "Enable Instagram Personal (session)") }

		Button(onClick = { store.clearProvider("instagram"); store.appendLog("settings:wipe instagram") }) { Text("Wipe Instagram credentials") }

		Text("On-device model (quantized)")
		Switch(checked = useQuant.value, onCheckedChange = {
			useQuant.value = it
			store.setUseQuantizedModel(it)
			store.appendLog("settings:quant ${it}")
			if (it) {
				try { TfliteClassifier(context).classify("warmup") } catch (_: Throwable) {}
				onMessage?.invoke("Quantized model enabled")
			} else onMessage?.invoke("Quantized model disabled")
		})

		Text("On-device LLM (tiny)")
		Switch(checked = useLlm.value, onCheckedChange = {
			useLlm.value = it
			store.setUseLlm(it)
			store.appendLog("settings:llm ${it}")
			val llm = LlamaEngine(context)
			val present = llm.modelPresent()
			modelPresent.value = present
			if (it) {
				onMessage?.invoke(
					when {
						llm.isReady() -> "LLM enabled"
						present -> "LLM model present; engine not yet enabled"
						else -> "LLM enabled but model missing (~210 MB)"
					}
				)
				if (!present) showLlmPrompt.value = true
			} else onMessage?.invoke("LLM disabled")
		})

		Text("Why download? The tiny LLM double-checks borderline comments to reduce false positives.")
		Text("Size: ~210 MB, stored on-device. No data leaves your phone.")
		Text("Tip: Download on Wi‑Fi. CPU/battery are used only during scans.")

		Text(if (modelPresent.value) "LLM model present" else "LLM model missing (~210 MB)")
		if (!modelPresent.value) {
			Button(onClick = {
				if (downloading.value) return@Button
				downloading.value = true
				downloadMsg.value = "Resolving model and downloading (~210 MB, Wi‑Fi recommended)..."
				CoroutineScope(Dispatchers.IO).launch {
					val ok = LlmDownloader.resolveAndDownload(
						context,
						repoId = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
						quantSuffix = "Q4_K_M"
					)
					downloading.value = false
					modelPresent.value = LlamaEngine(context).modelPresent()
					store.appendLog(if (ok) "llm:download ok" else "llm:download fail")
					onMessage?.invoke(if (ok) "LLM model downloaded" else "LLM download failed")
				}
			}, enabled = !downloading.value) { Text("Resolve + Download (~210 MB, Wi‑Fi)") }
			if (downloadMsg.value.isNotEmpty()) Text(downloadMsg.value)
		}

		if (showLlmPrompt.value) {
			AlertDialog(
				onDismissRequest = { showLlmPrompt.value = false },
				title = { Text("Download LLM model") },
				text = { Text("The tiny LLM helps with borderline cases to improve accuracy. Download size is ~210 MB. Stored on-device; no data is sent to servers. Recommended on Wi‑Fi.") },
				confirmButton = {
					TextButton(onClick = {
						showLlmPrompt.value = false
						if (!downloading.value) {
							downloading.value = true
							downloadMsg.value = "Resolving model and downloading (~210 MB, Wi‑Fi recommended)..."
							CoroutineScope(Dispatchers.IO).launch {
								val ok = LlmDownloader.resolveAndDownload(
									context,
									repoId = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
									quantSuffix = "Q4_K_M"
								)
								downloading.value = false
								modelPresent.value = LlamaEngine(context).modelPresent()
								store.appendLog(if (ok) "llm:download ok" else "llm:download fail")
								onMessage?.invoke(if (ok) "LLM model downloaded" else "LLM download failed")
							}
						}
					}) { Text("Resolve + Download (~210 MB)") }
				},
				dismissButton = { TextButton(onClick = { showLlmPrompt.value = false }) { Text("Later") } }
			)
		}

		Text("Max comments per URL scan")
		val maxPerUrl = remember { mutableStateOf(store.getMaxCommentsPerUrl().toString()) }
		OutlinedTextField(value = maxPerUrl.value, onValueChange = {
			maxPerUrl.value = it.filter { ch -> ch.isDigit() }.take(5)
			it.filter { ch -> ch.isDigit() }.toIntOrNull()?.let { v -> store.setMaxCommentsPerUrl(v) }
		}, singleLine = true)

		Button(onClick = { onOpenManualTest?.invoke() }) { Text("Local AI Training") }
	}
}
