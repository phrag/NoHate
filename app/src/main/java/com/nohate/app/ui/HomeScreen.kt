package com.nohate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.nohate.app.data.SecureStore
import com.nohate.app.work.ScanWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(onMessage: (String) -> Unit, onOpenReview: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SecureStore(context) }
    val scope = rememberCoroutineScope()

    var minutes by remember { mutableStateOf(store.getIntervalMinutes()) }
    var isMonitoring by remember { mutableStateOf(false) }
    var lastScanAt by remember { mutableStateOf(store.getLastScanAt()) }
    var lastScanTotal by remember { mutableStateOf(store.getLastScanTotal()) }
    var lastScanFlagged by remember { mutableStateOf(store.getLastScanFlagged()) }
    var scanTotal by remember { mutableStateOf(store.getScanProgressTotal()) }
    var scanDone by remember { mutableStateOf(store.getScanProgressDone()) }
    var scanMsg by remember { mutableStateOf(store.getScanProgressMsg()) }
    var sessionActive by remember { mutableStateOf(store.getSessionCookies("instagram") != null) }
    val monitoredUrls = remember { mutableStateOf(store.getMonitoredUrls()) }
    var newUrl by remember { mutableStateOf("") }
    val isScanning = scanTotal > 0 && scanDone < scanTotal

    LaunchedEffect(true) {
        while (true) {
            val at = store.getLastScanAt()
            val tot = store.getLastScanTotal()
            val flg = store.getLastScanFlagged()
            if (at != lastScanAt || tot != lastScanTotal || flg != lastScanFlagged) {
                lastScanAt = at; lastScanTotal = tot; lastScanFlagged = flg
            }
            scanTotal = store.getScanProgressTotal()
            scanDone = store.getScanProgressDone()
            scanMsg = store.getScanProgressMsg()
            sessionActive = store.getSessionCookies("instagram") != null
            delay(1000)
        }
    }

    val scanProgress = if (scanTotal > 0) scanDone.toFloat() / scanTotal.toFloat() else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Hero scan status card
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(56.dp).semantics { contentDescription = "Scan in progress" },
                                    progress = { scanProgress },
                                    strokeWidth = 4.dp,
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(56.dp),
                                    progress = { 1f },
                                    strokeWidth = 4.dp,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (isScanning) "Scanning…" else "Idle",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val whenStr = if (lastScanAt == 0L) "Never scanned"
                            else "Last scan: ${DateFormat.getDateTimeInstance().format(Date(lastScanAt))}"
                            Text(whenStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (isScanning) {
                        LinearProgressIndicator(
                            progress = { scanProgress },
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Scan progress ${scanDone} of ${scanTotal}" },
                        )
                        Text("${scanMsg} (${scanDone}/${scanTotal})", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text("${lastScanTotal} scanned") })
                        AssistChip(
                            onClick = onOpenReview,
                            label = { Text("${lastScanFlagged} flagged") },
                            modifier = Modifier.semantics { contentDescription = "View ${lastScanFlagged} flagged comments" },
                        )
                        AssistChip(onClick = {}, label = { Text(if (sessionActive) "Session on" else "No session") })
                    }
                }
            }
        }

        // Action buttons
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Actions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                WorkManager.getInstance(context).enqueue(
                                    OneTimeWorkRequestBuilder<ScanWorker>().build()
                                )
                                onMessage("Scan started")
                            },
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Start a one-time scan now" },
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(" Scan now", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(
                            onClick = {
                                if (!isMonitoring) {
                                    store.setIntervalMinutes(minutes)
                                    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                                        "comment-scan",
                                        ExistingPeriodicWorkPolicy.UPDATE,
                                        PeriodicWorkRequestBuilder<ScanWorker>(minutes.toLong(), TimeUnit.MINUTES).build()
                                    )
                                    WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ScanWorker>().build())
                                    isMonitoring = true
                                    onMessage("Monitoring every ${minutes} min")
                                } else {
                                    WorkManager.getInstance(context).cancelUniqueWork("comment-scan")
                                    isMonitoring = false
                                    onMessage("Monitoring stopped")
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(if (isMonitoring) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                            Text(if (isMonitoring) " Stop" else " Monitor", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = onOpenReview, modifier = Modifier.weight(1f)) {
                            Text("Review", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Monitored posts
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Monitored posts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (monitoredUrls.value.isEmpty()) {
                        Text(
                            "No posts monitored. Add an Instagram post URL to include it in each scan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        monitoredUrls.value.forEachIndexed { idx, url ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    url,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(onClick = {
                                    store.removeMonitoredUrlAt(idx)
                                    monitoredUrls.value = store.getMonitoredUrls()
                                    onMessage("Removed")
                                }) { Text("Remove") }
                            }
                        }
                        if (monitoredUrls.value.size > 1) {
                            TextButton(onClick = {
                                store.clearMonitoredUrls()
                                monitoredUrls.value = store.getMonitoredUrls()
                            }) { Text("Clear all") }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = newUrl,
                            onValueChange = { newUrl = it },
                            label = { Text("Instagram post URL") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(
                            onClick = {
                                val u = newUrl.trim()
                                if (u.isNotEmpty()) {
                                    store.addMonitoredUrl(u)
                                    monitoredUrls.value = store.getMonitoredUrls()
                                    newUrl = ""
                                    onMessage("Added to monitor list")
                                }
                            },
                            enabled = newUrl.isNotBlank(),
                        ) { Text("Add") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
