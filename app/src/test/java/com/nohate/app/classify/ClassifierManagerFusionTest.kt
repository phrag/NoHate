package com.nohate.app.classify

import com.nohate.app.classify.ClassifierManager.Companion.RULES_ESCALATE_MIN
import com.nohate.app.classify.ClassifierManager.Companion.fuse
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fusion rules for the primary signal.
 *
 * The regression these guard: the pipeline used to take max() across the rules
 * core and the neural models as equal peers, which unions their false
 * positives. The Rust lexicon was flagging most benign comments, so it set the
 * floor for every scan regardless of what the neural model said.
 */
class ClassifierManagerFusionTest {

	private fun nn(p: Float) = Score(p, if (p >= 0.5f) "hate" else "not_hate", 0L, "toxic-distilbert-int8")
	private fun rules(p: Float) = Score(p, if (p >= 0.5f) "hate" else "not_hate", 0L, RulesClassifier.ID)

	@Test
	fun `no backends at all yields no signal`() {
		assertEquals(0f, fuse(null, null).probability, 1e-6f)
		assertEquals("none", fuse(null, null).backend)
	}

	@Test
	fun `neural verdict passes through when rules are quiet`() {
		val s = fuse(nn(0.91f), rules(0f))
		assertEquals(0.91f, s.probability, 1e-6f)
	}

	@Test
	fun `a soft rules hit cannot override a calm neural verdict`() {
		// This is the old false-positive path: the lexicon scored a benign
		// comment mid-range and max() promoted it straight to flagged.
		val s = fuse(nn(0.02f), rules(0.80f))
		assertEquals(
			"soft rules hit must not escalate",
			0.02f, s.probability, 1e-6f
		)
	}

	@Test
	fun `an unambiguous rules hit does override a calm neural verdict`() {
		// A slur or ADL coded term the neural model missed — exactly the
		// "88" / "1488" / "6MWE" case.
		val s = fuse(nn(0.02f), rules(0.95f))
		assertEquals(0.95f, s.probability, 1e-6f)
		assertEquals(RulesClassifier.ID, s.backend)
	}

	@Test
	fun `escalation threshold is inclusive`() {
		val s = fuse(nn(0.1f), rules(RULES_ESCALATE_MIN))
		assertEquals(RULES_ESCALATE_MIN, s.probability, 1e-6f)
	}

	@Test
	fun `rules just below the threshold do not escalate`() {
		val s = fuse(nn(0.1f), rules(RULES_ESCALATE_MIN - 0.01f))
		assertEquals(0.1f, s.probability, 1e-6f)
	}

	@Test
	fun `rules stand in as primary when no neural model is ready`() {
		// Fresh install / model still downloading: a noisy classifier beats
		// none, so the escalation bar does not apply.
		val s = fuse(null, rules(0.60f))
		assertEquals(0.60f, s.probability, 1e-6f)
		assertEquals(RulesClassifier.ID, s.backend)
	}

	@Test
	fun `neural verdict wins when it is already higher than rules`() {
		val s = fuse(nn(0.97f), rules(0.90f))
		assertEquals(0.97f, s.probability, 1e-6f)
	}
}
