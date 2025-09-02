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

@Composable
fun ReviewScreen() {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val items = remember { mutableStateOf(store.getFlaggedItems()) }
	val hidden = remember { mutableStateOf(store.getHiddenItems()) }
	val clipboard = LocalClipboardManager.current

	LaunchedEffect(Unit) {
		items.value = store.getFlaggedItems()
		hidden.value = store.getHiddenItems()
	}

	Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
		Text("Review flagged comments", style = MaterialTheme.typography.titleLarge)
		if (items.value.isEmpty()) {
			Text("Nothing to review.")
		} else {
			Button(onClick = {
				// Clear all
				store.setFlaggedItems(emptyList())
				items.value = emptyList()
			}) { Text("Clear all") }
		}
		LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
			items(items.value.size) { idx ->
				val item = items.value[idx]
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
							IconButton(onClick = {
								store.correctFalsePositive(idx)
								items.value = store.getFlaggedItems()
							}) { Icon(Icons.Filled.CheckCircle, contentDescription = "Not hate") }
							IconButton(onClick = {
								store.hideFlaggedItemAt(idx)
								items.value = store.getFlaggedItems()
								hidden.value = store.getHiddenItems()
							}) { Icon(Icons.Filled.Visibility, contentDescription = "Hide") }
							IconButton(onClick = {
								store.removeFlaggedItemAt(idx)
								items.value = store.getFlaggedItems()
							}) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
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
