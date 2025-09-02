package com.nohate.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nohate.app.data.SecureStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.work.OneTimeWorkRequestBuilder
import androidx.compose.material3.FilterChip
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Flag
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen() {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val graph = remember { com.nohate.app.platform.InstagramGraph(context) { SecureStore(context).getOAuthToken("ig_graph") } }
	val items = remember { mutableStateOf(store.getFlaggedItems()) }
	val hidden = remember { mutableStateOf(store.getHiddenItems()) }
	val clipboard = LocalClipboardManager.current
	val showAll = remember { mutableStateOf(false) }
	val lastComments = remember { mutableStateOf(store.getLastComments()) }
	val lastScanAt = remember { mutableStateOf(store.getLastScanAt()) }

	LaunchedEffect(Unit) {
		items.value = store.getFlaggedItems()
		hidden.value = store.getHiddenItems()
		lastComments.value = store.getLastComments()
		lastScanAt.value = store.getLastScanAt()
	}

	LaunchedEffect(true) {
		while (true) {
			val at = store.getLastScanAt()
			if (at != lastScanAt.value) {
				lastScanAt.value = at
				items.value = store.getFlaggedItems()
				hidden.value = store.getHiddenItems()
				lastComments.value = store.getLastComments()
			}
			delay(1000)
		}
	}

	Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
		Text("Review comments", style = MaterialTheme.typography.titleLarge)
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			FilterChip(selected = !showAll.value, onClick = { showAll.value = false }, label = { Text("Flagged only") })
			FilterChip(selected = showAll.value, onClick = { showAll.value = true }, label = { Text("All last scan") })
		}

		val displayItems = if (showAll.value) lastComments.value.map { com.nohate.app.data.FlaggedItem(text = it, sourceUrl = null) } else items.value
		if (displayItems.isEmpty()) {
			Text("Nothing to review.")
			Text("Tips:")
			Text("• Run a scan from Home")
			Text("• Import a public post via Local AI Training")
			val context = LocalContext.current
			Button(onClick = {
				val req = OneTimeWorkRequestBuilder<com.nohate.app.work.ScanWorker>().build()
				androidx.work.WorkManager.getInstance(context).enqueue(req)
			}) { Text("Run scan now") }
			// Show last few scan logs for context
			val store = remember { SecureStore(context) }
			val recent = store.getLogs().takeLast(5).map { it.substringAfter(":", it).trim() }.filter { it.startsWith("scan:") || it.startsWith("provider:") }
			if (recent.isNotEmpty()) {
				Text("Recent:")
				recent.forEach { Text(it) }
			}
		} else {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Button(onClick = {
					if (showAll.value) {
						store.setLastComments(emptyList())
						lastComments.value = emptyList()
					} else {
						store.setFlaggedItems(emptyList())
						items.value = emptyList()
					}
				}) { Text("Clear all") }
				TextButton(onClick = { com.nohate.app.platform.InstagramIntents.openReportHelp(context) }) { Text("How to report") }
			}
		}
		LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
			items(displayItems.size) { idx ->
				val item = displayItems[idx]
				ElevatedCard {
					Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(item.text)
						Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
							IconButton(onClick = {
								clipboard.setText(AnnotatedString(item.text))
							}) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }
							IconButton(onClick = {
								val share = Intent(Intent.ACTION_SEND).apply {
									type = "text/plain"
									putExtra(Intent.EXTRA_TEXT, item.text)
								}
								context.startActivity(Intent.createChooser(share, "Share comment"))
							}) { Icon(Icons.Filled.Share, contentDescription = "Share") }
							item.sourceUrl?.let { url ->
								IconButton(onClick = {
									context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
								}) { Icon(Icons.Filled.Info, contentDescription = "Open") }
							}
							if (!showAll.value) {
								IconButton(onClick = {
									store.correctFalsePositive(idx)
									items.value = store.getFlaggedItems()
								}) { Icon(Icons.Filled.CheckCircle, contentDescription = "Not hate") }
								IconButton(onClick = {
									store.hideFlaggedItemAt(idx)
									items.value = store.getFlaggedItems()
									// update hidden list below
								}) { Icon(Icons.Filled.Visibility, contentDescription = "Hide") }
								IconButton(onClick = {
									store.removeFlaggedItemAt(idx)
									items.value = store.getFlaggedItems()
								}) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
							} else {
								IconButton(onClick = {
									val text = item.text
									store.appendFlaggedItems(listOf(com.nohate.app.data.FlaggedItem(text = text, sourceUrl = item.sourceUrl)))
									store.enqueueTraining(listOf(text))
								}) { Icon(Icons.Filled.Flag, contentDescription = "Flag as hate") }
							}
							// Report: open source URL or let user share to Instagram
							IconButton(onClick = {
								item.sourceUrl?.let { url ->
									com.nohate.app.platform.InstagramIntents.openPost(context, url)
								} ?: run {
									val share = Intent(Intent.ACTION_SEND).apply {
										type = "text/plain"
										putExtra(Intent.EXTRA_TEXT, item.text)
									}
									context.startActivity(Intent.createChooser(share, "Report via Instagram"))
								}
								SecureStore(context).incReported()
							}) { Icon(Icons.Filled.Info, contentDescription = "Report") }

							// Instagram Graph API moderation (owned media only)
							if (graph.isAuthorized()) {
								val commentId = store.getCommentIdForText(item.text)
								commentId?.let { cid ->
									val scope = rememberCoroutineScope()
									TextButton(onClick = {
										scope.launch(Dispatchers.IO) {
											val ok = graph.hideComment(cid, true)
											SecureStore(context).appendLog("ig:hide ${ok}")
										}
									}) { Text("Hide (IG)") }
									TextButton(onClick = {
										scope.launch(Dispatchers.IO) {
											val ok = graph.deleteComment(cid)
											SecureStore(context).appendLog("ig:delete ${ok}")
										}
									}) { Text("Delete (IG)") }
								}
							}
						}
					}
				}
			}
		}

		Text("Hidden", style = MaterialTheme.typography.titleMedium)
		if (hidden.value.isEmpty()) {
			Text("No hidden items.")
		} else {
			Button(onClick = { store.setHiddenItems(emptyList()); hidden.value = emptyList() }) { Text("Clear hidden") }
			LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				items(hidden.value.size) { idx ->
					val item = hidden.value[idx]
					ElevatedCard {
						Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
							Text(item.text)
							Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
								IconButton(onClick = {
									store.unhideHiddenItemAt(idx)
									hidden.value = store.getHiddenItems()
									items.value = store.getFlaggedItems()
								}) { Icon(Icons.Filled.Visibility, contentDescription = "Unhide") }
								IconButton(onClick = { store.removeHiddenItemAt(idx); hidden.value = store.getHiddenItems() }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
							}
						}
					}
				}
			}
		}
	}
}
