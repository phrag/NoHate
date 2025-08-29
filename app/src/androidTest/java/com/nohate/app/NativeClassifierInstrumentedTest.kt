package com.nohate.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeClassifierInstrumentedTest {
	@Test
	fun classify_detects_awful() {
		val score = NativeClassifier.classify("This is awful")
		assertTrue(score >= 0.5f)
	}

	@Test
	fun classify_detects_fuck_you() {
		val score = NativeClassifier.classify("fuck you")
		assertTrue(score >= 0.8f)
	}

	@Test
	fun classify_ignores_positive() {
		val score = NativeClassifier.classify("Great post!")
		assertFalse(score >= 0.8f)
	}

	@Test
	fun classify_with_user_lexicon_boosts_score() {
		val text = "this has zzz_unique_phrase"
		val base = NativeClassifier.classify(text)
		val boosted = NativeClassifier.classifyWithUser(text, listOf("zzz_unique_phrase"), emptyList())
		assertTrue("Expected boosted >= base", boosted >= base)
		assertTrue("Expected boosted to be at least 0.6", boosted >= 0.6f)
	}
}
