package com.nohate.app.classify

import android.content.Context
import com.nohate.app.data.SecureStore

/**
 * Picks and applies the primary + borderline classifiers for a scan.
 *
 * Primary signals (ready ONNX models + the Rust rules core) are combined via
 * max(); when the resulting score falls inside the calibration band around the
 * user's flagging threshold, the optional TinyLlama LLM is invoked as a
 * second opinion.
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
