package com.nohate.app.classify

import com.nohate.app.NativeClassifier

class RulesClassifier(
	private val userHate: () -> List<String> = { emptyList() },
	private val userSafe: () -> List<String> = { emptyList() },
) : Classifier {
	override val info: ClassifierInfo = ClassifierInfo(
		id = ID,
		displayName = "Rules (Rust core)",
		version = "0.1.0",
		sizeBytes = 0L,
		estPeakMemMb = 2,
		license = "GPL-3.0",
		languages = listOf("en"),
		source = ClassifierInfo.Source.BUILT_IN,
	)

	override fun isReady(): Boolean = true

	override fun classify(text: String): Score {
		val t0 = System.nanoTime()
		val p = NativeClassifier.classifyWithUser(text, userHate(), userSafe())
		val dt = System.nanoTime() - t0
		val label = if (p >= 0.5f) "hate" else "not_hate"
		return Score(probability = p, label = label, latencyNanos = dt, backend = ID)
	}

	companion object { const val ID = "rules-v1" }
}
