package com.nohate.app.classify

import android.content.Context
import com.nohate.app.ml.TfliteClassifier

/**
 * Adapter that exposes the legacy TFLite stub classifier through the Classifier interface.
 * Kept during the Phase 1 transition; users can opt in via Settings (`isUseQuantizedModel`).
 */
class LegacyTfliteClassifier(context: Context) : Classifier {
	private val impl = TfliteClassifier(context)

	override val info: ClassifierInfo = ClassifierInfo(
		id = ID,
		displayName = "TFLite (legacy stub)",
		version = "0.0.1",
		sizeBytes = 0L,
		estPeakMemMb = 20,
		license = "Apache-2.0",
		languages = listOf("en"),
		source = ClassifierInfo.Source.BUNDLED,
	)

	override fun isReady(): Boolean = true

	override fun classify(text: String): Score {
		val t0 = System.nanoTime()
		val p = impl.classify(text)
		val dt = System.nanoTime() - t0
		val label = if (p >= 0.5f) "hate" else "not_hate"
		return Score(probability = p, label = label, latencyNanos = dt, backend = ID)
	}

	companion object { const val ID = "tflite-stub" }
}
