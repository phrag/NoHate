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
import com.nohate.app.ml.TfliteClassifier
import com.nohate.app.llm.LlamaEngine
import com.nohate.app.llm.LlmEngine
import android.util.Log

class ScanWorker(
	appContext: Context,	params: WorkerParameters
) : CoroutineWorker(appContext, params) {
	override suspend fun doWork(): Result {
		val store = SecureStore(applicationContext)
		val manual = inputData.getString(KEY_MANUAL_COMMENTS)
		val comments: List<String> = if (!manual.isNullOrBlank()) {
			manual.split('\u0001', '\n').map { it.trim() }.filter { it.isNotEmpty() }
		} else {
			val provider: CommentProvider = selectProvider(store)
			provider.fetchRecentComments()
		}
		val userHate = store.getUserHatePhrases()
		val userSafe = store.getUserSafePhrases()
		val threshold = store.getFlagThreshold()
		val useQuant = store.isUseQuantizedModel()
		val tfl = if (useQuant) TfliteClassifier(applicationContext) else null
		val useLlm = store.isUseLlm()
		val llm: LlmEngine? = if (useLlm) LlamaEngine(applicationContext).takeIf { it.isReady() } else null
		Log.d(TAG, "scan start comments=${comments.size} quant=$useQuant llmToggle=$useLlm llmReady=${llm != null} thr=${"%.2f".format(threshold)}")
		val flagged = comments.filter { comment ->
			try {
				val rulesScore = NativeClassifier.classifyWithUser(comment, userHate, userSafe)
				val modelScore = tfl?.classify(comment) ?: 0f
				var finalScore = maxOf(rulesScore, modelScore)
				if (finalScore in (threshold - 0.2f)..threshold && llm != null) {
					val res = llm.classify(comment, LlamaEngine.PROMPT)
					Log.d(TAG, "llm used text='${comment.take(40)}' rules=${"%.2f".format(rulesScore)} tfl=${"%.2f".format(modelScore)} llm=${"%.2f".format(res.score)}")
					finalScore = maxOf(finalScore, res.score)
				}
				val isFlagged = finalScore >= threshold
				Log.d(TAG, "decision text='${comment.take(40)}' score=${"%.2f".format(finalScore)} flagged=$isFlagged")
				isFlagged
			} catch (t: Throwable) {
				Log.e(TAG, "classify error", t)
				false
			}
		}
		if (flagged.isNotEmpty()) {
			store.appendFlaggedComments(flagged)
			Log.d(TAG, "flagged saved count=${flagged.size}")
		} else {
			Log.d(TAG, "no comments flagged")
		}
		return Result.success()
	}

	private fun selectProvider(store: SecureStore): CommentProvider {
		val graphEnabled = store.isFeatureEnabled("ig_graph")
		val sessionEnabled = store.isFeatureEnabled("ig_session")
		val provider = when {
			graphEnabled -> InstagramGraphProvider(applicationContext)
			sessionEnabled -> InstagramSessionProvider(applicationContext)
			else -> InstagramProvider(applicationContext)
		}
		Log.d(TAG, "provider=${provider.javaClass.simpleName} graph=$graphEnabled session=$sessionEnabled")
		return provider
	}

	companion object {
		private const val TAG = "ScanWorker"
		const val KEY_MANUAL_COMMENTS = "manual_comments"
	}
}
