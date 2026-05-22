package com.nohate.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.nohate.app.data.FlaggedItem
import com.nohate.app.data.SecureStore
import com.nohate.app.platform.InstagramGraph
import com.nohate.app.platform.InstagramIntents
import com.nohate.app.work.ScanWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen() {
    val context = LocalContext.current
    val store = remember { SecureStore(context) }
    val graph = remember { InstagramGraph(context) { SecureStore(context).getOAuthToken("ig_graph") } }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val items = remember { mutableStateOf(store.getFlaggedItems()) }
    val hidden = remember { mutableStateOf(store.getHiddenItems()) }
    val lastComments = remember { mutableStateOf(store.getLastComments()) }
    val lastScanAt = remember { mutableStateOf(store.getLastScanAt()) }
    val showAll = remember { mutableStateOf(false) }
    val showHidden = remember { mutableStateOf(false) }

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

    val displayItems: List<FlaggedItem> = if (showAll.value)
        lastComments.value.map { FlaggedItem(text = it, sourceUrl = null) }
    else
        items.value

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Filter chips
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showAll.value,
                    onClick = { showAll.value = false },
                    label = { Text("Flagged (${items.value.size})") },
                )
                FilterChip(
                    selected = showAll.value,
                    onClick = { showAll.value = true },
                    label = { Text("All last scan (${lastComments.value.size})") },
                )
                FilterChip(
                    selected = showHidden.value,
                    onClick = { showHidden.value = !showHidden.value },
                    label = { Text("Hidden (${hidden.value.size})") },
                )
            }
        }

        // Empty state
        if (!showHidden.value && displayItems.isEmpty()) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Nothing to review", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (showAll.value) "No comments from the last scan." else "No flagged comments. Run a scan or lower the threshold.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(onClick = {
                            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ScanWorker>().build())
                        }) { Text("Scan now") }
                    }
                }
            }
        }

        // Clear button row when items exist
        if (!showHidden.value && displayItems.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        if (showAll.value) {
                            store.setLastComments(emptyList()); lastComments.value = emptyList()
                        } else {
                            store.setFlaggedItems(emptyList()); items.value = emptyList()
                        }
                    }) { Text("Clear all", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { InstagramIntents.openReportHelp(context) }) { Text("How to report") }
                }
            }
        }

        // Comment cards (flagged / all-scan view)
        if (!showHidden.value) {
            itemsIndexed(displayItems) { idx, item ->
                CommentCard(
                    item = item,
                    showAll = showAll.value,
                    graphReady = graph.isAuthorized(),
                    commentId = store.getCommentIdForText(item.text),
                    onCopy = { clipboard.setText(AnnotatedString(item.text)) },
                    onShare = {
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, item.text) },
                            "Share comment"
                        ))
                    },
                    onOpen = { item.sourceUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
                    onReport = {
                        item.sourceUrl?.let { InstagramIntents.openPost(context, it) }
                            ?: context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, item.text) },
                                "Report via Instagram"
                            ))
                        store.incReported()
                    },
                    onNotHate = {
                        store.correctFalsePositive(idx); items.value = store.getFlaggedItems()
                    },
                    onHide = {
                        store.hideFlaggedItemAt(idx); items.value = store.getFlaggedItems()
                        hidden.value = store.getHiddenItems()
                    },
                    onDelete = { store.removeFlaggedItemAt(idx); items.value = store.getFlaggedItems() },
                    onFlagHate = {
                        store.appendFlaggedItems(listOf(FlaggedItem(text = item.text, sourceUrl = item.sourceUrl)))
                        store.enqueueTraining(listOf(item.text))
                    },
                    onMarkSafe = { store.addUserSafePhrase(item.text) },
                    onIgHide = { cid ->
                        scope.launch(Dispatchers.IO) { graph.hideComment(cid, true).also { store.appendLog("ig:hide $it") } }
                    },
                    onIgDelete = { cid ->
                        scope.launch(Dispatchers.IO) { graph.deleteComment(cid).also { store.appendLog("ig:delete $it") } }
                    },
                )
            }
        }

        // Hidden section
        if (showHidden.value) {
            if (hidden.value.isEmpty()) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("No hidden comments.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    TextButton(onClick = { store.setHiddenItems(emptyList()); hidden.value = emptyList() }) {
                        Text("Clear all hidden", color = MaterialTheme.colorScheme.error)
                    }
                }
                itemsIndexed(hidden.value) { idx, item ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(item.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = {
                                        store.unhideHiddenItemAt(idx)
                                        hidden.value = store.getHiddenItems()
                                        items.value = store.getFlaggedItems()
                                    }) { Icon(Icons.Filled.Visibility, contentDescription = "Unhide") }
                                    IconButton(onClick = {
                                        store.removeHiddenItemAt(idx)
                                        hidden.value = store.getHiddenItems()
                                    }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                                }
                            },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CommentCard(
    item: FlaggedItem,
    showAll: Boolean,
    graphReady: Boolean,
    commentId: String?,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onReport: () -> Unit,
    onNotHate: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onFlagHate: () -> Unit,
    onMarkSafe: () -> Unit,
    onIgHide: (String) -> Unit,
    onIgDelete: (String) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                item.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            item.sourceUrl?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider()
            // Primary actions row
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onCopy, modifier = Modifier.semantics { contentDescription = "Copy comment" }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                }
                IconButton(onClick = onShare, modifier = Modifier.semantics { contentDescription = "Share comment" }) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                }
                if (item.sourceUrl != null) {
                    IconButton(onClick = onOpen, modifier = Modifier.semantics { contentDescription = "Open original post" }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    }
                }
                IconButton(onClick = onReport, modifier = Modifier.semantics { contentDescription = "Report via Instagram" }) {
                    Icon(Icons.Filled.Flag, contentDescription = null)
                }
            }
            // Secondary actions
            if (!showAll) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onNotHate) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Text(" Not hate")
                    }
                    TextButton(onClick = onHide) {
                        Icon(Icons.Filled.VisibilityOff, contentDescription = null)
                        Text(" Hide")
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text(" Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                // Instagram Graph moderation (owned media)
                if (graphReady && commentId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onIgHide(commentId) }) { Text("Hide on IG") }
                        TextButton(onClick = { onIgDelete(commentId) }) { Text("Delete on IG", color = MaterialTheme.colorScheme.error) }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = onFlagHate) { Text("Flag as hate") }
                    TextButton(onClick = onMarkSafe) { Text("Mark safe") }
                }
            }
        }
    }
}
