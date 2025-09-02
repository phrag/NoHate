package com.nohate.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.material3.OutlinedTextField
import com.nohate.app.platform.PostImporter
import androidx.work.workDataOf

@Composable
fun SettingsScreen(onOpenManualTest: (() -> Unit)? = null, onMessage: ((String) -> Unit)? = null) {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val minutes = remember { mutableStateOf(store.getIntervalMinutes()) }
	val graphEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_graph")) }
	val sessionEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_session")) }
	val useQuant = remember { mutableStateOf(store.isUseQuantizedModel()) }
	val useLlm = remember { mutableStateOf(store.isUseLlm()) }
	val threshold = remember { mutableStateOf(store.getFlagThreshold()) }
	val manualComments = remember { mutableStateOf("") }
	val postUrl = remember { mutableStateOf("") }

	Column(
		modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text("Settings", style = MaterialTheme.typography.titleLarge)

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
			onMessage?.invoke("Threshold set to ${String.format("%.2f", threshold.value)}")
		}) { Text("Save threshold") }

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

		Text("On-device model (quantized)")
		Switch(checked = useQuant.value, onCheckedChange = {
			useQuant.value = it
			store.setUseQuantizedModel(it)
			if (it) {
				try { TfliteClassifier(context).classify("warmup") } catch (_: Throwable) {}
				onMessage?.invoke("Quantized model enabled")
			} else onMessage?.invoke("Quantized model disabled")
		})

		Text("On-device LLM (tiny)")
		Switch(checked = useLlm.value, onCheckedChange = {
			useLlm.value = it
			store.setUseLlm(it)
			if (it) {
				val llm = LlamaEngine(context)
				val present = llm.modelPresent()
				onMessage?.invoke(
					when {
						llm.isReady() -> "LLM enabled"
						present -> "LLM model present; engine not yet enabled"
						else -> "LLM not ready (model missing)"
					}
				)
			} else onMessage?.invoke("LLM disabled")
		})

		Button(onClick = {
			val engine = LlamaEngine(context)
			if (!engine.isReady()) {
				onMessage?.invoke("LLM not ready")
			} else {
				val res = engine.classify("I hate you", LlamaEngine.PROMPT)
				val msg = "LLM test: ${res.label} (${String.format("%.2f", res.score)})"
				onMessage?.invoke(msg)
			}
		}) { Text("Test LLM classify") }

		Button(onClick = {
			val req = OneTimeWorkRequestBuilder<ScanWorker>().build()
			WorkManager.getInstance(context).enqueueUniqueWork("manual_scan", ExistingWorkPolicy.REPLACE, req)
			onMessage?.invoke("Scan enqueued")
		}) { Text("Run scan now") }

		Text("Manual comments (one per line)")
		OutlinedTextField(value = manualComments.value, onValueChange = { manualComments.value = it }, minLines = 3)
		Button(onClick = {
			val payload = manualComments.value.trim()
			if (payload.isEmpty()) { onMessage?.invoke("Enter comments first") } else {
				val data = workDataOf(ScanWorker.KEY_MANUAL_COMMENTS to payload)
				val req = OneTimeWorkRequestBuilder<ScanWorker>().setInputData(data).build()
				WorkManager.getInstance(context).enqueueUniqueWork("manual_scan", ExistingWorkPolicy.REPLACE, req)
				onMessage?.invoke("Manual scan enqueued (${payload.lines().size} comments)")
			}
		}) { Text("Run manual scan") }

		Text("Public Instagram post URL")
		OutlinedTextField(value = postUrl.value, onValueChange = { postUrl.value = it }, minLines = 1)
		Button(onClick = {
			val url = postUrl.value.trim()
			if (url.isEmpty()) { onMessage?.invoke("Enter a URL first") } else {
				CoroutineScope(Dispatchers.IO).launch {
					val comments = PostImporter.fetchPublicComments(url, limit = 50)
					if (comments.isEmpty()) {
						onMessage?.invoke("No comments fetched (URL or visibility)")
					} else {
						val payload = comments.joinToString("\u0001")
						val data = workDataOf(ScanWorker.KEY_MANUAL_COMMENTS to payload)
						WorkManager.getInstance(context).enqueueUniqueWork(
							"manual_scan", ExistingWorkPolicy.REPLACE,
							OneTimeWorkRequestBuilder<ScanWorker>().setInputData(data).build()
						)
						onMessage?.invoke("Fetched ${comments.size} comments; scan enqueued")
					}
				}
			}
		}) { Text("Fetch from URL + scan") }

		Button(onClick = {
			CoroutineScope(Dispatchers.IO).launch {
				val ok = LlmDownloader.resolveAndDownload(
					context,
					repoId = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
					quantSuffix = "Q4_K_M"
				)
				if (ok) onMessage?.invoke("LLM model downloaded") else onMessage?.invoke("LLM download failed")
			}
		}) { Text("Resolve + Download TinyLlama (Q4_K_M)") }

		Button(onClick = { onOpenManualTest?.invoke() }) { Text("Local AI Training") }
	}
}
