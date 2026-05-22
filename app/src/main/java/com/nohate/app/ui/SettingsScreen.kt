package com.nohate.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nohate.app.auth.SessionLoginActivity
import com.nohate.app.data.SecureStore
import com.nohate.app.llm.LlamaEngine
import com.nohate.app.llm.LlmDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenManualTest: (() -> Unit)? = null,
    onMessage: ((String) -> Unit)? = null,
    onOpenOnboarding: (() -> Unit)? = null,
    onOpenBenchmark: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val store = remember { SecureStore(context) }

    val minutes = remember { mutableStateOf(store.getIntervalMinutes()) }
    val graphEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_graph")) }
    val sessionEnabled = remember { mutableStateOf(store.isFeatureEnabled("ig_session")) }
    val useLlm = remember { mutableStateOf(store.isUseLlm()) }
    val threshold = remember { mutableStateOf(store.getFlagThreshold()) }
    val modelPresent = remember { mutableStateOf(LlamaEngine(context).modelPresent()) }
    val showLlmPrompt = remember { mutableStateOf(false) }
    val downloading = remember { mutableStateOf(false) }
    val downloadMsg = remember { mutableStateOf("") }
    val maxPerUrl = remember { mutableStateOf(store.getMaxCommentsPerUrl().toString()) }

    androidx.compose.runtime.LaunchedEffect(true) {
        while (true) {
            modelPresent.value = LlamaEngine(context).modelPresent()
            kotlinx.coroutines.delay(5000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // ── Calibration ──────────────────────────────────────────────────────
        SectionHeader(icon = { Icon(Icons.Filled.Tune, contentDescription = null) }, title = "Calibration")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Flag threshold: ${String.format("%.2f", threshold.value)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = threshold.value,
                    onValueChange = { threshold.value = it.coerceIn(0.5f, 0.95f) },
                    valueRange = 0.5f..0.95f,
                    modifier = Modifier.semantics { contentDescription = "Flag threshold slider, currently ${String.format("%.2f", threshold.value)}" },
                )
                Text("Lower = more aggressive, higher = more selective.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = {
                    store.setFlagThreshold(threshold.value)
                    store.appendLog("settings:threshold ${String.format("%.2f", threshold.value)}")
                    onMessage?.invoke("Threshold saved")
                }) { Text("Save threshold") }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Text("Scan interval: ${minutes.value} min", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = minutes.value.toFloat(),
                    onValueChange = { minutes.value = it.toInt().coerceIn(15, 120) },
                    valueRange = 15f..120f,
                    steps = 6,
                    modifier = Modifier.semantics { contentDescription = "Scan interval slider, currently ${minutes.value} minutes" },
                )
                FilledTonalButton(onClick = { store.setIntervalMinutes(minutes.value) }) { Text("Save interval") }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Text("Max comments per URL", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = maxPerUrl.value,
                    onValueChange = {
                        maxPerUrl.value = it.filter { ch -> ch.isDigit() }.take(5)
                        it.filter { ch -> ch.isDigit() }.toIntOrNull()?.let { v -> store.setMaxCommentsPerUrl(v) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── Models ────────────────────────────────────────────────────────────
        SectionHeader(icon = { Icon(Icons.Filled.Memory, contentDescription = null) }, title = "Models")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                ListItem(
                    headlineContent = { Text("TinyLlama borderline model") },
                    supportingContent = {
                        Text(when {
                            useLlm.value && modelPresent.value -> "LLM ready — used for borderline cases"
                            useLlm.value -> "Enabled but model missing (~210 MB)"
                            else -> "Disabled"
                        })
                    },
                    trailingContent = {
                        Switch(
                            checked = useLlm.value,
                            onCheckedChange = {
                                useLlm.value = it
                                store.setUseLlm(it)
                                store.appendLog("settings:llm ${it}")
                                val llm = LlamaEngine(context)
                                modelPresent.value = llm.modelPresent()
                                if (it && !llm.modelPresent()) showLlmPrompt.value = true
                                onMessage?.invoke(if (it) "LLM enabled" else "LLM disabled")
                            },
                        )
                    },
                )
                if (!modelPresent.value) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (downloadMsg.value.isNotEmpty()) {
                            Text(downloadMsg.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(
                            onClick = {
                                if (downloading.value) return@FilledTonalButton
                                downloading.value = true
                                downloadMsg.value = "Downloading ~210 MB (Wi-Fi recommended)…"
                                CoroutineScope(Dispatchers.IO).launch {
                                    val ok = LlmDownloader.resolveAndDownload(
                                        context,
                                        repoId = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
                                        quantSuffix = "Q4_K_M"
                                    )
                                    downloading.value = false
                                    modelPresent.value = LlamaEngine(context).modelPresent()
                                    store.appendLog(if (ok) "llm:download ok" else "llm:download fail")
                                    downloadMsg.value = if (ok) "Downloaded successfully" else "Download failed — retry on Wi-Fi"
                                    onMessage?.invoke(if (ok) "LLM model ready" else "LLM download failed")
                                }
                            },
                            enabled = !downloading.value,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Text("  Download TinyLlama (~210 MB)")
                        }
                    }
                }
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Benchmark classifiers") },
                    supportingContent = { Text("Measure latency, throughput, accuracy") },
                    trailingContent = {
                        TextButton(onClick = { onOpenBenchmark?.invoke() }) {
                            Icon(Icons.Filled.Analytics, contentDescription = null)
                            Text("  Open")
                        }
                    },
                )
            }
        }

        // ── Connectors ────────────────────────────────────────────────────────
        SectionHeader(icon = { Icon(Icons.Filled.Hub, contentDescription = null) }, title = "Connectors")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                ListItem(
                    headlineContent = { Text("Instagram Business / Creator") },
                    supportingContent = { Text("OAuth + PKCE — needed for Graph API moderation") },
                    trailingContent = {
                        Switch(
                            checked = graphEnabled.value,
                            onCheckedChange = {
                                graphEnabled.value = it
                                store.setFeatureEnabled("ig_graph", it)
                                store.appendLog("settings:ig_graph $it")
                            },
                        )
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Instagram Personal (session)") },
                    supportingContent = { Text("Opt-in session cookies — no credentials stored") },
                    trailingContent = {
                        Switch(
                            checked = sessionEnabled.value,
                            onCheckedChange = {
                                sessionEnabled.value = it
                                store.setFeatureEnabled("ig_session", it)
                                store.appendLog("settings:ig_session $it")
                                if (it) context.startActivity(Intent(context, SessionLoginActivity::class.java))
                            },
                        )
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Wipe Instagram credentials") },
                    supportingContent = { Text("Removes all stored tokens and cookies") },
                    trailingContent = {
                        TextButton(onClick = {
                            store.clearProvider("instagram")
                            store.appendLog("settings:wipe instagram")
                            onMessage?.invoke("Instagram credentials wiped")
                        }) { Text("Wipe", color = MaterialTheme.colorScheme.error) }
                    },
                )
            }
        }

        // ── Privacy ───────────────────────────────────────────────────────────
        SectionHeader(icon = { Icon(Icons.Filled.Security, contentDescription = null) }, title = "Privacy")
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                ListItem(
                    headlineContent = { Text("Re-run setup wizard") },
                    supportingContent = { Text("Walk through onboarding again") },
                    trailingContent = {
                        TextButton(onClick = { onOpenOnboarding?.invoke() }) {
                            Icon(Icons.Filled.Login, contentDescription = null)
                            Text("  Open")
                        }
                    },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Local AI Training") },
                    supportingContent = { Text("Label comments to improve on-device accuracy") },
                    trailingContent = {
                        TextButton(onClick = { onOpenManualTest?.invoke() }) { Text("Open") }
                    },
                )
            }
        }

        // ── Danger zone ───────────────────────────────────────────────────────
        SectionHeader(icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, title = "Danger zone", error = true)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("These actions clear stored data and cannot be undone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(
                    onClick = {
                        store.setFlaggedItems(emptyList())
                        store.setHiddenItems(emptyList())
                        onMessage?.invoke("Flagged items cleared")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear all flagged items") }
                FilledTonalButton(
                    onClick = {
                        store.clearMonitoredUrls()
                        onMessage?.invoke("Monitored posts cleared")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear monitored posts") }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showLlmPrompt.value) {
        AlertDialog(
            onDismissRequest = { showLlmPrompt.value = false },
            title = { Text("Download TinyLlama") },
            text = { Text("~210 MB on Wi-Fi. Stored on-device; no data leaves your phone. Used only for borderline cases.") },
            confirmButton = {
                TextButton(onClick = {
                    showLlmPrompt.value = false
                    if (!downloading.value) {
                        downloading.value = true
                        downloadMsg.value = "Downloading…"
                        CoroutineScope(Dispatchers.IO).launch {
                            val ok = LlmDownloader.resolveAndDownload(
                                context,
                                repoId = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
                                quantSuffix = "Q4_K_M"
                            )
                            downloading.value = false
                            modelPresent.value = LlamaEngine(context).modelPresent()
                            downloadMsg.value = if (ok) "Downloaded" else "Failed"
                        }
                    }
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { showLlmPrompt.value = false }) { Text("Later") } },
        )
    }
}

@Composable
private fun SectionHeader(
    icon: @Composable () -> Unit,
    title: String,
    error: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}
