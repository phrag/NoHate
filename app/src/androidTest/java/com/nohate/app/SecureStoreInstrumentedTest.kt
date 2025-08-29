package com.nohate.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nohate.app.data.SecureStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureStoreInstrumentedTest {
	@Test
	fun interval_set_get() {
		val ctx = InstrumentationRegistry.getInstrumentation().targetContext
		val store = SecureStore(ctx)
		store.setIntervalMinutes(45)
		assertEquals(45, store.getIntervalMinutes())
	}

	@Test
	fun feature_flags_set_get() {
		val ctx = InstrumentationRegistry.getInstrumentation().targetContext
		val store = SecureStore(ctx)
		store.setFeatureEnabled("test_flag", true)
		assertEquals(true, store.isFeatureEnabled("test_flag"))
	}

	@Test
	fun provider_tokens_set_clear() {
		val ctx = InstrumentationRegistry.getInstrumentation().targetContext
		val store = SecureStore(ctx)
		store.setOAuthToken("instagram", "token123")
		store.setSessionCookies("instagram", "sessionid=abc;")
		store.clearProvider("instagram")
		assertNull(store.getOAuthToken("instagram"))
		assertNull(store.getSessionCookies("instagram"))
	}
}
