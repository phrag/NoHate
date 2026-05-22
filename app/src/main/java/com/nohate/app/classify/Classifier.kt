package com.nohate.app.classify

data class Score(
	val probability: Float,
	val label: String,
	val latencyNanos: Long,
	val backend: String,
)

data class ClassifierInfo(
	val id: String,
	val displayName: String,
	val version: String,
	val sizeBytes: Long,
	val estPeakMemMb: Int,
	val license: String,
	val languages: List<String>,
	val source: Source,
) {
	enum class Source { BUNDLED, DOWNLOADED, BUILT_IN }
}

interface Classifier {
	val info: ClassifierInfo
	fun isReady(): Boolean
	suspend fun warmup() {}
	fun classify(text: String): Score
	fun close() {}
}
