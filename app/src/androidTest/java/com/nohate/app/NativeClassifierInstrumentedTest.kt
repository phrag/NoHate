package com.nohate.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeClassifierInstrumentedTest {
	@Test
	fun classify_detects_awful() {
		val score = NativeClassifier.classify("This is awful")
		assertTrue(score >= 0.8f)
	}

	@Test
	fun classify_ignores_positive() {
		val score = NativeClassifier.classify("Great post!")
		assertFalse(score >= 0.8f)
	}
}
