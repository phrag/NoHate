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
import com.nohate.app.data.FlaggedItem

class ScanWorker(
	appContext: Context,	params: WorkerParameters
) : CoroutineWorker(appContext, params) {
	override suspend fun doWork(): Result {
		val store = SecureStore(applicationContext)
		val manual = inputData.getString(KEY_MANUAL_COMMENTS)
		val sourceUrl = inputData.getString(KEY_SOURCE_URL)
		val comments: List<String> = if (!manual.isNullOrBlank()) {
			manual.split('\u0001', '\n').map { it.trim() }.filter { it.isNotEmpty() }
		} else {
			val provider: CommentProvider = selectProvider(store)
			provider.fetchRecentComments()
		}
		store.appendLog("scan:start count=${comments.size}")
		val userHate = store.getUserHatePhrases()
		val userSafe = store.getUserSafePhrases()
		val threshold = store.getFlagThreshold()
		val useQuant = store.isUseQuantizedModel()
		val tfl = if (useQuant) TfliteClassifier(applicationContext) else null
		val useLlm = store.isUseLlm()
		val llm: LlmEngine? = if (useLlm) LlamaEngine(applicationContext).takeIf { it.isReady() } else null
		Log.d(TAG, "scan start comments=${comments.size} quant=$useQuant llmToggle=$useLlm llmReady=${llm != null} thr=${"%.2f".format(threshold)}")
		val flaggedTexts = comments.filter { comment ->
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
				store.appendLog("scan:decision score=${"%.2f".format(finalScore)} flagged=$isFlagged text='${comment.take(40)}'")
				isFlagged
			} catch (t: Throwable) {
				Log.e(TAG, "classify error", t)
				store.appendLog("scan:error ${t.message ?: t.javaClass.simpleName}")
				false
			}
		}
		if (flaggedTexts.isNotEmpty()) {
			val items = flaggedTexts.map { FlaggedItem(text = it, sourceUrl = sourceUrl) }
			store.appendFlaggedItems(items)
			Log.d(TAG, "flagged saved count=${items.size}")
			store.appendLog("scan:flagged count=${items.size}")
		} else {
			Log.d(TAG, "no comments flagged")
			store.appendLog("scan:flagged count=0")
		}
		store.setLastScan(System.currentTimeMillis(), total = comments.size, flagged = flaggedTexts.size)
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
		store.appendLog("provider:${provider.javaClass.simpleName}")
		return provider
	}

	companion object {
		private const val TAG = "ScanWorker"
		const val KEY_MANUAL_COMMENTS = "manual_comments"
		const val KEY_SOURCE_URL = "source_url"
	}
}
