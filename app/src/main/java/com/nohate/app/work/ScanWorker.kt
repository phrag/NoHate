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
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import com.nohate.app.MainActivity

class ScanWorker(
	appContext: Context,	params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private fun createForegroundInfo(text: String): androidx.work.ForegroundInfo {
        val channelId = "scan_status"
        val mgr = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mgr.getNotificationChannel(channelId) == null) {
                mgr.createNotificationChannel(NotificationChannel(channelId, "Scanning", NotificationManager.IMPORTANCE_LOW))
            }
        }
        val openIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(applicationContext, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification: Notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("NoHate")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "View results", pendingOpen)
            .build()
        return androidx.work.ForegroundInfo(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun notifyDone(flagged: Int) {
        val channelId = "scan_status"
        val openIntent = Intent(applicationContext, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(applicationContext, 0, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("NoHate")
            .setContentText(if (flagged > 0) "Scan complete: flagged ${flagged}" else "Scan complete: no issues")
            .setAutoCancel(true)
            .addAction(0, "View results", pendingOpen)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(1002, notification)
    }
	override suspend fun doWork(): Result {
		val store = SecureStore(applicationContext)
		val manual = inputData.getString(KEY_MANUAL_COMMENTS)
		val sourceUrl = inputData.getString(KEY_SOURCE_URL)
		val comments: List<String> = when {
			!manual.isNullOrBlank() -> {
				manual.split('\u0001', '\n').map { it.trim() }.filter { it.isNotEmpty() }
			}
			!sourceUrl.isNullOrBlank() -> {
				store.appendLog("scan:url ${sourceUrl}")
				try {
					com.nohate.app.platform.PostImporter.fetchPublicComments(sourceUrl!!, limit = 200)
				} catch (t: Throwable) {
					Log.w(TAG, "url fetch failed", t)
					emptyList()
				}
			}
			else -> {
				val provider: CommentProvider = selectProvider(store)
				val base = provider.fetchRecentComments()
				// Also include monitored public post URLs
				val extra = mutableListOf<String>()
				val urls = store.getMonitoredUrls()
				if (urls.isNotEmpty()) {
					store.appendLog("scan:monitored urls=${urls.size}")
					try {
						urls.forEach { u ->
							try {
								extra += com.nohate.app.platform.PostImporter.fetchPublicComments(u, limit = 200)
							} catch (t: Throwable) {
								Log.w(TAG, "monitored fetch failed", t)
							}
						}
					} catch (_: Throwable) { }
				}
				(base + extra).distinct()
			}
		}
		// Save recent comments for review-all
		store.setLastComments(comments.takeLast(500))
		store.setScanProgress(total = comments.size, done = 0, message = "Starting scan")
		store.appendLog("scan:start count=${comments.size}")
		setForeground(createForegroundInfo("Scanning ${comments.size} comments"))
		val userHate = store.getUserHatePhrases()
		val userSafe = store.getUserSafePhrases()
		val threshold = store.getFlagThreshold()
		val useQuant = store.isUseQuantizedModel()
		val tfl = if (useQuant) TfliteClassifier(applicationContext) else null
		val useLlm = store.isUseLlm()
		val llm: LlmEngine? = if (useLlm) LlamaEngine(applicationContext).takeIf { it.isReady() } else null
		Log.d(TAG, "scan start comments=${comments.size} quant=$useQuant llmToggle=$useLlm llmReady=${llm != null} thr=${"%.2f".format(threshold)}")
		var processed = 0
		val flaggedTexts = comments.filter { comment ->
			try {
				val rulesScore = NativeClassifier.classifyWithUser(comment, userHate, userSafe)
				val modelScore = tfl?.classify(comment) ?: 0f
				var finalScore = maxOf(rulesScore, modelScore)
				if (finalScore in (threshold - 0.2f)..threshold && llm != null) {
					store.incLlmInvocations()
					val res = llm.classify(comment, LlamaEngine.PROMPT)
					Log.d(TAG, "llm used text='${comment.take(40)}' rules=${"%.2f".format(rulesScore)} tfl=${"%.2f".format(modelScore)} llm=${"%.2f".format(res.score)}")
					finalScore = maxOf(finalScore, res.score)
				}
				// Apply explicit user overrides last: user-hate forces flag, user-safe forces not-flag
				val lc = comment.lowercase()
				val hateOverride = userHate.any { it.isNotBlank() && lc.contains(it) }
				val safeOverride = userSafe.any { it.isNotBlank() && lc.contains(it) }
				var isFlagged = finalScore >= threshold
				var overrideNote = ""
				if (hateOverride) { isFlagged = true; overrideNote = " override=hate" }
				else if (safeOverride) { isFlagged = false; overrideNote = " override=safe" }
				store.appendLog("scan:decision score=${"%.2f".format(finalScore)} flagged=$isFlagged${overrideNote} text='${comment.take(40)}'")
				processed += 1
				if (processed % 5 == 0 || processed == comments.size) {
					store.setScanProgress(total = comments.size, done = processed, message = "Classified ${processed}/${comments.size}")
				}
				isFlagged
			} catch (t: Throwable) {
				Log.e(TAG, "classify error", t)
				store.appendLog("scan:error ${t.message ?: t.javaClass.simpleName}")
				false
			}
		}
		// De-duplicate against already flagged and hidden items
		val alreadyFlagged = store.getFlaggedItems().map { it.text }.toSet()
		val alreadyHidden = store.getHiddenItems().map { it.text }.toSet()
		val newFlaggedTexts = flaggedTexts.filter { it !in alreadyFlagged && it !in alreadyHidden }
		if (newFlaggedTexts.isNotEmpty()) {
			val items = newFlaggedTexts.map { FlaggedItem(text = it, sourceUrl = sourceUrl) }
			store.appendFlaggedItems(items)
			store.enqueueTraining(newFlaggedTexts)
			Log.d(TAG, "flagged saved count=${items.size} (dedup from ${flaggedTexts.size})")
			store.appendLog("scan:flagged count=${items.size}")
			notifyDone(flagged = items.size)
		} else {
			Log.d(TAG, "no new comments flagged")
			store.appendLog("scan:flagged count=0")
			notifyDone(flagged = 0)
		}
		val now = System.currentTimeMillis()
		store.setLastScan(now, total = comments.size, flagged = flaggedTexts.size)
		store.appendScanHistory(now, total = comments.size, flagged = flaggedTexts.size)
		store.addProcessedCount(comments.size)
		store.setScanProgress(total = comments.size, done = comments.size, message = "Done")
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
