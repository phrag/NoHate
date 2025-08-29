package com.nohate.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nohate.app.NativeClassifier
import com.nohate.app.data.SecureStore
import com.nohate.app.platform.CommentProvider
import com.nohate.app.platform.InstagramGraphProvider
import com.nohate.app.platform.InstagramSessionProvider
import com.nohate.app.platform.InstagramProvider

class ScanWorker(
	appContext: Context,	params: WorkerParameters
) : CoroutineWorker(appContext, params) {
	override suspend fun doWork(): Result {
		val store = SecureStore(applicationContext)
		val provider: CommentProvider = selectProvider(store)
		val comments = provider.fetchRecentComments()
		val flagged = comments.filter { comment ->
			try {
				val score = NativeClassifier.classify(comment)
				score >= 0.8f
			} catch (t: Throwable) {
				false
			}
		}
		if (flagged.isNotEmpty()) {
			store.appendFlaggedComments(flagged)
		}
		return Result.success()
	}

	private fun selectProvider(store: SecureStore): CommentProvider {
		val graphEnabled = store.isFeatureEnabled("ig_graph")
		val sessionEnabled = store.isFeatureEnabled("ig_session")
		return when {
			graphEnabled -> InstagramGraphProvider(applicationContext)
			sessionEnabled -> InstagramSessionProvider(applicationContext)
			else -> InstagramProvider(applicationContext)
		}
	}
}
