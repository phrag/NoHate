package com.nohate.app.classify

import android.content.Context
import com.nohate.app.data.SecureStore

/**
 * Picks and applies the primary + borderline classifiers for a scan.
 *
 * Phase 1 keeps the existing behaviour: rules + (optional) TFLite stub as the
 * primary signals combined via max(), and the TinyLlama LLM as a borderline
 * second opinion when the score lands inside the calibration band around the
 * user's flagging threshold.
 *
 * Phase 2 will let the user select a specific primary id from a richer set
 * (ONNX transformer classifiers) via `SecureStore`.
 */
class ClassifierManager(
	context: Context,
	private val registry: ClassifierRegistry = ClassifierRegistry(context),
) {
	private val store = SecureStore(context)

	val threshold: Float = store.getFlagThreshold()
	val band: Float = store.getLlmBandWidth(threshold)

	/** Ids of every primary backend currently selectable, in priority order. */
	fun primaryIds(): List<String> =
		registry.available().filter { it != LlmClassifier.ID }

	/**
	 * Score the comment against every available primary backend and return the
	 * max probability — matches the legacy ScanWorker semantics.
	 */
	fun classifyPrimary(text: String): Score {
		var best = Score(0f, "not_hate", 0L, "none")
		for (id in primaryIds()) {
			val c = registry.get(id) ?: continue
			val s = c.classify(text)
			if (s.probability > best.probability) best = s
		}
		return best
	}

	/**
	 * Returns the borderline classifier (TinyLlama) if it's enabled and ready,
	 * else null. Callers should only invoke this when the primary score falls
	 * inside `[threshold - band, threshold]`.
	 */
	fun borderlineOrNull(): Classifier? {
		if (!store.isUseLlm()) return null
		return registry.get(LlmClassifier.ID)
	}

	fun isInBorderlineBand(score: Float): Boolean =
		score in (threshold - band)..threshold

	fun close() = registry.close()
}
