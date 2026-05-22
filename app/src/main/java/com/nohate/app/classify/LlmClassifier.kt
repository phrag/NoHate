package com.nohate.app.classify

import android.content.Context
import com.nohate.app.llm.LlamaEngine

/**
 * Wraps the existing LlamaEngine so the WorkManager pipeline can request a
 * borderline-band second opinion through the same Classifier surface as every
 * other backend.
 */
class LlmClassifier(context: Context) : Classifier {
	private val engine = LlamaEngine(context)

	override val info: ClassifierInfo = ClassifierInfo(
		id = ID,
		displayName = "TinyLlama 1.1B (Q4_K_M)",
		version = "1.0.0",
		sizeBytes = 640L * 1024 * 1024,
		estPeakMemMb = 600,
		license = "Apache-2.0",
		languages = listOf("en"),
		source = ClassifierInfo.Source.DOWNLOADED,
	)

	override fun isReady(): Boolean = engine.isReady()

	override fun classify(text: String): Score {
		val t0 = System.nanoTime()
		val res = engine.classify(text, LlamaEngine.PROMPT)
		val dt = System.nanoTime() - t0
		return Score(probability = res.score, label = res.label, latencyNanos = dt, backend = ID)
	}

	companion object { const val ID = "tinyllama-1.1b-q4km" }
}
