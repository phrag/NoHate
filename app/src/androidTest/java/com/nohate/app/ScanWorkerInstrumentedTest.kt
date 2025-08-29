package com.nohate.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.nohate.app.data.SecureStore
import com.nohate.app.work.ScanWorker
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanWorkerInstrumentedTest {
	private lateinit var context: Context

	@Before
	fun setup() {
		context = ApplicationProvider.getApplicationContext()
		SecureStore(context).apply {
			setFeatureEnabled("ig_graph", false)
			setFeatureEnabled("ig_session", false)
			addUserHatePhrase("awful") // boost "This is awful" above threshold
		}
	}

	@Test
	fun scan_flags_comments() {
		val worker = TestListenableWorkerBuilder<ScanWorker>(context).build()
		val result = worker.startWork().get()
		assertTrue(result is ListenableWorker.Result.Success)
		val flagged = SecureStore(context).getFlaggedComments()
		assertTrue(flagged.isNotEmpty())
	}
}
