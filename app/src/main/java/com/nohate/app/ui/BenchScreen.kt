package com.nohate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nohate.app.bench.BenchOutcome
import com.nohate.app.bench.BenchRunSummary
import com.nohate.app.bench.BenchRunner
import com.nohate.app.classify.ClassifierRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BenchScreen(onMessage: ((String) -> Unit)? = null) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()

	val available = remember { ClassifierRegistry(context).also { /* discover */ }.available() }
	val selected: SnapshotStateMap<String, Boolean> = remember {
		mutableStateMapOf<String, Boolean>().apply { available.forEach { put(it, true) } }
	}

	var running by remember { mutableStateOf(false) }
	var phase by remember { mutableStateOf("") }
	var summary by remember { mutableStateOf<BenchRunSummary?>(null) }

	Column(
		modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Text("Benchmark", style = MaterialTheme.typography.titleLarge)
		Text(
			"Compares every available classifier on this device. Latency, throughput, memory and accuracy (against the bundled ETHOS-style eval set).",
			style = MaterialTheme.typography.bodyMedium,
		)
		ElevatedCard {
			Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text("Classifiers", style = MaterialTheme.typography.titleMedium)
				if (available.isEmpty()) {
					Text("None ready. Enable an option in Settings or bundle a model.")
				} else {
					available.forEach { id ->
						Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
							Checkbox(
								checked = selected[id] ?: false,
								onCheckedChange = { v -> selected[id] = v },
								enabled = !running,
							)
							Text(id)
						}
					}
				}
			}
		}

		FilledTonalButton(
			onClick = {
				if (running) return@FilledTonalButton
				val ids = available.filter { selected[it] == true }
				if (ids.isEmpty()) {
					onMessage?.invoke("Select at least one classifier")
					return@FilledTonalButton
				}
				running = true
				phase = "starting"
				summary = null
				scope.launch {
					val result = withContext(Dispatchers.Default) {
						val runner = BenchRunner(context)
						try {
							runner.run(ids) { id, p -> phase = "$id : $p" }
						} finally {
							runner.close()
						}
					}
					summary = result
					running = false
					phase = "done"
				}
			},
			enabled = !running,
		) { Text(if (running) "Running…" else "Run benchmark") }

		if (running) {
			Text("Phase: $phase", style = MaterialTheme.typography.bodySmall)
		}

		summary?.let { s -> ResultsCard(s) }

		summary?.let { s ->
			var savedPath by remember { mutableStateOf<String?>(null) }
			FilledTonalButton(onClick = {
				try {
					val runner = BenchRunner(context)
					val file = runner.exportJson(s)
					runner.close()
					savedPath = file.absolutePath
					onMessage?.invoke("Saved to ${file.name}")
				} catch (t: Throwable) {
					onMessage?.invoke("Export failed: ${t.message}")
				}
			}) { Text("Export JSON to app storage") }
			savedPath?.let { Text("Saved: $it", style = MaterialTheme.typography.bodySmall) }
		}
	}
}

@Composable
private fun ResultsCard(summary: BenchRunSummary) {
	ElevatedCard {
		Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text("Device: ${summary.device}", style = MaterialTheme.typography.bodySmall)
			Text(summary.androidVersion, style = MaterialTheme.typography.bodySmall)
			summary.outcomes.forEach { o -> OutcomeRow(o) }
		}
	}
}

@Composable
private fun OutcomeRow(o: BenchOutcome) {
	Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
		Text(o.classifierDisplayName, style = MaterialTheme.typography.titleSmall)
		if (o.errorMessage != null) {
			Text("error: ${o.errorMessage}", style = MaterialTheme.typography.bodySmall)
			return@Column
		}
		val cold = o.cold
		val warm = o.warm
		if (cold != null && warm != null) {
			Text(
				"cold p50/p95: ${"%.2f".format(cold.p50Ms)}/${"%.2f".format(cold.p95Ms)} ms · " +
					"warm p50/p95: ${"%.2f".format(warm.p50Ms)}/${"%.2f".format(warm.p95Ms)} ms",
				style = MaterialTheme.typography.bodySmall,
			)
		}
		o.throughput?.let {
			Text("throughput: ${"%.1f".format(it.perSecond)} comments/sec", style = MaterialTheme.typography.bodySmall)
		}
		o.memory?.let {
			Text(
				"mem: pss=${it.pssPeakKb / 1024} MB (Δ${it.pssDeltaKb / 1024} MB)",
				style = MaterialTheme.typography.bodySmall,
			)
		}
		val acc = o.accuracyBest ?: o.accuracyDefault
		acc?.let {
			Text(
				"acc@t=${"%.2f".format(it.threshold)}: " +
					"P=${"%.2f".format(it.precision)} R=${"%.2f".format(it.recall)} " +
					"F1=${"%.2f".format(it.f1)}" +
					(it.auc?.let { a -> " AUC=${"%.2f".format(a)}" } ?: ""),
				style = MaterialTheme.typography.bodySmall,
			)
		}
	}
}
