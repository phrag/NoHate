package com.nohate.app.bench

/**
 * Per-percentile latency reading (milliseconds). `n` is the number of
 * samples that produced the stats.
 */
data class LatencyStats(
	val p50Ms: Double,
	val p95Ms: Double,
	val p99Ms: Double,
	val meanMs: Double,
	val n: Int,
)

data class ThroughputStats(val perSecond: Double)

data class MemoryStats(
	val pssPeakKb: Long,
	val pssDeltaKb: Long,
	val heapUsedMb: Long,
)

data class ConfusionMatrix(val tp: Int, val fp: Int, val tn: Int, val fn: Int) {
	val precision: Double get() = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
	val recall: Double get() = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
	val f1: Double get() {
		val p = precision; val r = recall
		return if (p + r == 0.0) 0.0 else 2 * p * r / (p + r)
	}
}

data class AccuracyStats(
	val threshold: Float,
	val confusion: ConfusionMatrix,
	val auc: Double?,
) {
	val precision: Double get() = confusion.precision
	val recall: Double get() = confusion.recall
	val f1: Double get() = confusion.f1
}

data class BenchOutcome(
	val classifierId: String,
	val classifierDisplayName: String,
	val cold: LatencyStats?,
	val warm: LatencyStats?,
	val throughput: ThroughputStats?,
	val memory: MemoryStats?,
	val accuracyDefault: AccuracyStats?,
	val accuracyBest: AccuracyStats?,
	val errorMessage: String? = null,
)

data class BenchRunSummary(
	val device: String,
	val androidVersion: String,
	val timestampMs: Long,
	val outcomes: List<BenchOutcome>,
)
