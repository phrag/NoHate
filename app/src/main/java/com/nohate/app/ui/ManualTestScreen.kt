package com.nohate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nohate.app.NativeClassifier
import androidx.compose.ui.platform.LocalContext
import com.nohate.app.data.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nohate.app.platform.PostImporter
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nohate.app.work.ScanWorker
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun ManualTestScreen(onOpenReview: (() -> Unit)? = null) {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val input = remember { mutableStateOf("") }
	val score = remember { mutableStateOf<Float?>(null) }
	val error = remember { mutableStateOf<String?>(null) }
	val manualComments = remember { mutableStateOf("") }
	val postUrl = remember { mutableStateOf("") }
	val isBusy = remember { mutableStateOf(false) }
	val status = remember { mutableStateOf("") }
	val scope = rememberCoroutineScope()

	fun enqueueBatches(comments: List<String>, sourceUrl: String? = null) {
		// WorkManager Data max ~10KB; batch to ~50 comments per request
		val batchSize = 50
		val batches = comments.chunked(batchSize)
		batches.forEach { chunk ->
			val payload = chunk.joinToString("\u0001")
			val data = if (sourceUrl != null) {
				workDataOf(ScanWorker.KEY_MANUAL_COMMENTS to payload, ScanWorker.KEY_SOURCE_URL to sourceUrl)
			} else workDataOf(ScanWorker.KEY_MANUAL_COMMENTS to payload)
			val req = OneTimeWorkRequestBuilder<ScanWorker>().setInputData(data).build()
			WorkManager.getInstance(context).enqueueUniqueWork(
				"manual_scan",
				ExistingWorkPolicy.APPEND,
				req
			)
		}
	}

	Column(
		modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text("Teach the app", style = MaterialTheme.typography.titleLarge)
		Text("Paste a comment below and tell us if it's harmful or okay. Your answers stay on this device and help the app improve over time.")
		Text("Quick tips: 1) No account needed. 2) You can also import a public post URL below. 3) The small LLM only double-checks borderline cases.")

		OutlinedTextField(
			value = input.value,
			onValueChange = { input.value = it },
			modifier = Modifier.fillMaxWidth(),
			label = { Text("Type or paste a comment") },
			minLines = 4
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
		}, enabled = input.value.isNotBlank()) { Text("Check this comment") }
		Button(onClick = {
			store.addUserHatePhrase(input.value)
			val s = NativeClassifier.classifyWithUser(input.value, store.getUserHatePhrases(), store.getUserSafePhrases())
			score.value = s
		}, enabled = input.value.isNotBlank()) { Text("Teach as harmful") }
		Button(onClick = {
			store.addUserSafePhrase(input.value)
			val s = NativeClassifier.classifyWithUser(input.value, store.getUserHatePhrases(), store.getUserSafePhrases())
			score.value = s
		}, enabled = input.value.isNotBlank()) { Text("Teach as okay") }

		score.value?.let {
			val flagged = it >= 0.8f
			Text("Result: ${if (flagged) "Harmful" else "Okay"} — Score ${"%.2f".format(it)}")
		}
		error.value?.let { Text("Error: ${it}") }

		if (isBusy.value || status.value.isNotEmpty()) {
			LinearProgressIndicator()
			Text(status.value)
		}

		Text("Train with your own list (one per line)")
		OutlinedTextField(value = manualComments.value, onValueChange = { manualComments.value = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
		Button(onClick = {
			val payload = manualComments.value.trim()
			if (payload.isNotEmpty()) {
				status.value = "Scan enqueued..."
				val comments = payload.lines().map { it.trim() }.filter { it.isNotEmpty() }
				enqueueBatches(comments)
				store.appendLog("scan:enqueue manual count=${comments.size}")
				onOpenReview?.invoke()
			}
		}) { Text("Scan my list") }

		Text("Import from a public Instagram post")
		OutlinedTextField(value = postUrl.value, onValueChange = { postUrl.value = it }, minLines = 1, modifier = Modifier.fillMaxWidth())
		Button(onClick = {
			val url = postUrl.value.trim()
			if (url.isNotEmpty()) {
				scope.launch {
					isBusy.value = true
					status.value = "Fetching URL..."
					store.appendLog("import:url ${url}")
					val comments = withContext(Dispatchers.IO) { PostImporter.fetchPublicComments(url, limit = 200) }
					if (comments.isEmpty()) {
						status.value = "No comments fetched (private/visibility or parsing)"
						store.appendLog("import:empty")
						isBusy.value = false
					} else {
						status.value = "Fetched ${comments.size} comments. Enqueuing scan..."
						enqueueBatches(comments, sourceUrl = url)
						store.appendLog("import:ok count=${comments.size}")
						isBusy.value = false
						onOpenReview?.invoke()
					}
				}
			}
		}) { Text("Fetch from URL and scan") }

		Spacer(modifier = Modifier.height(80.dp))
	}
}
