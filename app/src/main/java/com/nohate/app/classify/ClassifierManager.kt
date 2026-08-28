package com.nohate.app.classify

import android.content.Context
import com.nohate.app.data.SecureStore

/**
 * Picks and applies the primary + borderline classifiers for a scan.
 *
 * The neural (ONNX) models are the primary signal. The Rust rules core is an
 * **escalation** signal, not a peer: it only raises a score when it fires on
 * something unambiguous (a slur, an ADL-catalogued coded term), and otherwise
 * contributes nothing.
 *
 * The previous design took max() across the rules core *and* the neural models
 * together. Combining classifiers with max() takes the union of their false
 * positives, so the noisiest backend sets the floor for the whole pipeline —
 * the lexicon alone was flagging ~70% of benign comments and dragging every
 * scan up with it. Rules now have to clear [RULES_ESCALATE_MIN] to count.
 *
 * When the resulting score falls inside the calibration band around the user's
 * flagging threshold, the optional TinyLlama LLM is invoked as a second opinion.
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
	 * Score the comment against the available neural primaries, then let the
	 * rules core escalate only if it fired on something unambiguous.
	 *
	 * If no neural model is ready yet (fresh install, model still downloading)
	 * the rules core stands in as the primary — a noisy classifier beats no
	 * classifier at all.
	 */
	fun classifyPrimary(text: String): Score {
		val ids = primaryIds()

		var neural: Score? = null
		for (id in ids) {
			if (id == RulesClassifier.ID) continue
			val c = registry.get(id) ?: continue
			val s = c.classify(text)
			if (neural == null || s.probability > neural.probability) neural = s
		}

		val rules = if (RulesClassifier.ID in ids) {
			registry.get(RulesClassifier.ID)?.classify(text)
		} else null

		return fuse(neural, rules)
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

	companion object {
		/**
		 * Minimum rules-core score that may override a neural verdict.
		 *
		 * Calibrated against the tiers in `rust/core/src/lib.rs`: slurs (0.95),
		 * coded terms (0.85–0.95) and explicit dehumanisation clear this bar;
		 * the softer signals (bare numeric codes at 0.75, "go home" at 0.70,
		 * generic harassment at 0.65–0.70) do not, and are left for the neural
		 * model to judge.
		 */
		const val RULES_ESCALATE_MIN = 0.85f

		/** Score returned when no backend is available at all. */
		val NO_SIGNAL = Score(0f, "not_hate", 0L, "none")

		/**
		 * Fuse the best neural verdict with the rules-core verdict.
		 *
		 * Pure, so it can be exercised without a [Context]. Rules may only
		 * override the neural model on an unambiguous hit — unless there is no
		 * neural model, in which case rules stand in as the primary.
		 */
		fun fuse(neural: Score?, rules: Score?): Score {
			val base = neural ?: NO_SIGNAL
			if (rules == null) return base
			val canEscalate = neural == null || rules.probability >= RULES_ESCALATE_MIN
			return if (canEscalate && rules.probability > base.probability) rules else base
		}
	}
}
