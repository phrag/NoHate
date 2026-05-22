package com.nohate.app.bench

import android.content.Context
import android.os.Build
import android.os.Debug
import com.nohate.app.classify.Classifier
import com.nohate.app.classify.ClassifierRegistry
import com.nohate.app.data.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Runs one [BenchOutcome] per classifier. The caller passes in a list of
 * classifier ids; the registry resolves each one and the runner reports
 * latency, throughput, memory, and accuracy against the bundled eval CSV.
 *
 * Methodology — kept in sync with `docs/BENCHMARKS.md`:
 *   - Warmup: 10 classifications discarded.
 *   - Cold latency: `coldN` unique inputs (default 200), single-threaded.
 *   - Warm latency: `warmN` cycling inputs (default 1000).
 *   - Throughput: comments-per-second over the warm run.
 *   - Memory: PSS delta + heap snapshot around warmup + warm run.
 *   - Accuracy: full eval set; threshold sweep `0.05..0.95 step 0.05`.
 */
class BenchRunner(
	private val context: Context,
	private val registry: ClassifierRegistry = ClassifierRegistry(context),
	private val coldN: Int = 200,
	private val warmN: Int = 1000,
) {
	suspend fun run(
		classifierIds: List<String>,
		onProgress: (id: String, phase: String) -> Unit = { _, _ -> },
	): BenchRunSummary = withContext(Dispatchers.Default) {
		val outcomes = classifierIds.map { id ->
			runCatching { runOne(id, onProgress) }
				.getOrElse { t ->
					BenchOutcome(
						classifierId = id,
						classifierDisplayName = id,
						cold = null, warm = null, throughput = null,
						memory = null, accuracyDefault = null, accuracyBest = null,
						errorMessage = t.message ?: t.javaClass.simpleName,
					)
				}
		}
		BenchRunSummary(
			device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
			androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
			timestampMs = System.currentTimeMillis(),
			outcomes = outcomes,
		)
	}

	private suspend fun runOne(
		id: String,
		onProgress: (String, String) -> Unit,
	): BenchOutcome {
		val classifier = registry.get(id) ?: return BenchOutcome(
			classifierId = id,
			classifierDisplayName = id,
			cold = null, warm = null, throughput = null,
			memory = null, accuracyDefault = null, accuracyBest = null,
			errorMessage = "classifier not available",
		)

		onProgress(id, "warmup")
		classifier.warmup()
		val warmupCorpus = BenchSuite.latencyCorpus(context, size = 10)
		warmupCorpus.forEach { classifier.classify(it) }

		val memBefore = pssKb()
		onProgress(id, "cold")
		val coldStats = measureLatency(classifier, BenchSuite.latencyCorpus(context, size = coldN))

		onProgress(id, "warm")
		val warmCorpus = BenchSuite.latencyCorpus(context, size = warmN)
		val warmStart = System.nanoTime()
		val warmStats = measureLatency(classifier, warmCorpus)
		val warmDurationSec = (System.nanoTime() - warmStart) / 1e9
		val throughput = if (warmDurationSec > 0) warmCorpus.size / warmDurationSec else 0.0
		val memAfter = pssKb()

		onProgress(id, "accuracy")
		val accuracy = scoreAccuracy(classifier)

		return BenchOutcome(
			classifierId = id,
			classifierDisplayName = classifier.info.displayName,
			cold = coldStats,
			warm = warmStats,
			throughput = ThroughputStats(throughput),
			memory = MemoryStats(
				pssPeakKb = memAfter,
				pssDeltaKb = (memAfter - memBefore).coerceAtLeast(0),
				heapUsedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024L * 1024L),
			),
			accuracyDefault = accuracy?.first,
			accuracyBest = accuracy?.second,
		)
	}

	private fun measureLatency(classifier: Classifier, corpus: List<String>): LatencyStats {
		val nanos = LongArray(corpus.size)
		for (i in corpus.indices) {
			val t0 = System.nanoTime()
			classifier.classify(corpus[i])
			nanos[i] = System.nanoTime() - t0
		}
		nanos.sort()
		val toMs = { ns: Long -> ns / 1_000_000.0 }
		return LatencyStats(
			p50Ms = toMs(nanos[nanos.size / 2]),
			p95Ms = toMs(nanos[(nanos.size * 95) / 100]),
			p99Ms = toMs(nanos[(nanos.size * 99) / 100]),
			meanMs = nanos.average() / 1_000_000.0,
			n = nanos.size,
		)
	}

	/**
	 * Returns (accuracyAtUserThreshold, accuracyAtBestF1Threshold) or null if
	 * the eval set is empty.
	 */
	private fun scoreAccuracy(classifier: Classifier): Pair<AccuracyStats, AccuracyStats>? {
		val samples = BenchSuite.evalSamples(context)
		if (samples.isEmpty()) return null
		val scored = samples.map { it.isHate to classifier.classify(it.text).probability }

		val userThreshold = SecureStore(context).getFlagThreshold()
		val defaultStats = AccuracyStats(
			threshold = userThreshold,
			confusion = confusion(scored, userThreshold),
			auc = aucRank(scored),
		)

		var bestT = userThreshold
		var bestStats = defaultStats
		var t = 0.05f
		while (t <= 0.95f + 1e-6f) {
			val c = confusion(scored, t)
			if (c.f1 > bestStats.f1) {
				bestT = t
				bestStats = AccuracyStats(threshold = t, confusion = c, auc = defaultStats.auc)
			}
			t += 0.05f
		}
		return defaultStats to bestStats.copy(threshold = bestT)
	}

	private fun confusion(scored: List<Pair<Boolean, Float>>, threshold: Float): ConfusionMatrix {
		var tp = 0; var fp = 0; var tn = 0; var fn = 0
		for ((isHate, p) in scored) {
			val predHate = p >= threshold
			when {
				isHate && predHate -> tp++
				!isHate && predHate -> fp++
				!isHate && !predHate -> tn++
				else -> fn++
			}
		}
		return ConfusionMatrix(tp = tp, fp = fp, tn = tn, fn = fn)
	}

	/** Mann–Whitney U / rank-based AUC. O(n log n). */
	private fun aucRank(scored: List<Pair<Boolean, Float>>): Double? {
		val pos = scored.count { it.first }
		val neg = scored.size - pos
		if (pos == 0 || neg == 0) return null
		val sorted = scored.withIndex().sortedBy { it.value.second }
		var rankSumPos = 0.0
		var i = 0
		var rank = 1
		while (i < sorted.size) {
			var j = i
			while (j + 1 < sorted.size && sorted[j + 1].value.second == sorted[i].value.second) j++
			val avgRank = (rank + rank + (j - i)) / 2.0
			for (k in i..j) if (sorted[k].value.first) rankSumPos += avgRank
			rank += (j - i + 1)
			i = j + 1
		}
		val u = rankSumPos - pos * (pos + 1) / 2.0
		return u / (pos.toDouble() * neg.toDouble())
	}

	private fun pssKb(): Long {
		val info = Debug.MemoryInfo()
		Debug.getMemoryInfo(info)
		return info.totalPss.toLong()
	}

	fun exportJson(summary: BenchRunSummary, outDir: File = File(context.filesDir, "bench")): File {
		outDir.mkdirs()
		val out = File(outDir, "run-${summary.timestampMs}.json")
		val root = JSONObject().apply {
			put("device", summary.device)
			put("androidVersion", summary.androidVersion)
			put("timestampMs", summary.timestampMs)
			put("outcomes", JSONArray().apply {
				summary.outcomes.forEach { o -> put(outcomeToJson(o)) }
			})
		}
		out.writeText(root.toString(2))
		return out
	}

	private fun outcomeToJson(o: BenchOutcome): JSONObject = JSONObject().apply {
		put("classifierId", o.classifierId)
		put("classifierDisplayName", o.classifierDisplayName)
		put("cold", o.cold?.let(::latencyJson) ?: JSONObject.NULL)
		put("warm", o.warm?.let(::latencyJson) ?: JSONObject.NULL)
		put("throughputPerSec", o.throughput?.perSecond ?: JSONObject.NULL)
		put("memory", o.memory?.let { m ->
			JSONObject().apply {
				put("pssPeakKb", m.pssPeakKb)
				put("pssDeltaKb", m.pssDeltaKb)
				put("heapUsedMb", m.heapUsedMb)
			}
		} ?: JSONObject.NULL)
		put("accuracyDefault", o.accuracyDefault?.let(::accuracyJson) ?: JSONObject.NULL)
		put("accuracyBest", o.accuracyBest?.let(::accuracyJson) ?: JSONObject.NULL)
		o.errorMessage?.let { put("error", it) }
	}

	private fun latencyJson(s: LatencyStats) = JSONObject().apply {
		put("p50Ms", s.p50Ms); put("p95Ms", s.p95Ms); put("p99Ms", s.p99Ms)
		put("meanMs", s.meanMs); put("n", s.n)
	}

	private fun accuracyJson(a: AccuracyStats) = JSONObject().apply {
		put("threshold", a.threshold)
		put("precision", a.precision); put("recall", a.recall); put("f1", a.f1)
		put("auc", a.auc ?: JSONObject.NULL)
		put("confusion", JSONObject().apply {
			put("tp", a.confusion.tp); put("fp", a.confusion.fp)
			put("tn", a.confusion.tn); put("fn", a.confusion.fn)
		})
	}

	fun close() = registry.close()
}
