package com.nohate.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nohate.app.classify.ClassifierManager
import com.nohate.app.data.SecureStore
import com.nohate.app.platform.CommentProvider
import com.nohate.app.platform.InstagramGraphProvider
import com.nohate.app.platform.InstagramSessionProvider
import com.nohate.app.platform.InstagramProvider
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
		val myIgUserId = store.getIgUserId()
		val commentData: List<com.nohate.app.platform.CommentData> = when {
			!manual.isNullOrBlank() -> {
				manual.split('\u0001', '\n').map { it.trim() }.filter { it.isNotEmpty() }
					.map { com.nohate.app.platform.CommentData(text = it) }
			}
			!sourceUrl.isNullOrBlank() -> {
				store.appendLog("scan:url ${sourceUrl}")
				try {
					com.nohate.app.platform.PostImporter.fetchPublicCommentsRich(
						sourceUrl!!,
						limit = store.getMaxCommentsPerUrl(),
						cookies = store.getSessionCookies("instagram")
					)
				} catch (t: Throwable) {
					Log.w(TAG, "url fetch failed", t)
					emptyList()
				}
			}
			else -> {
				val provider: CommentProvider = selectProvider(store)
				val base = provider.fetchRecentComments().map { com.nohate.app.platform.CommentData(text = it) }
				val extra = mutableListOf<com.nohate.app.platform.CommentData>()
				val urls = store.getMonitoredUrls()
				if (urls.isNotEmpty()) {
					store.appendLog("scan:monitored urls=${urls.size}")
					urls.forEach { u ->
						try {
							extra += com.nohate.app.platform.PostImporter.fetchPublicCommentsRich(
								u,
								limit = store.getMaxCommentsPerUrl(),
								cookies = store.getSessionCookies("instagram")
							)
						} catch (t: Throwable) {
							Log.w(TAG, "monitored fetch failed", t)
						}
					}
				}
				(base + extra).distinctBy { it.text }
			}
		}
		val comments: List<String> = commentData.map { it.text }
		// Save recent comments for review-all
		store.setLastComments(comments.takeLast(500))
		store.setScanProgress(total = comments.size, done = 0, message = "Starting scan")
		store.appendLog("scan:start count=${comments.size}")
		setForeground(createForegroundInfo("Scanning ${comments.size} comments"))
		val userHate = store.getUserHatePhrases()
		val userSafe = store.getUserSafePhrases()
		val manager = ClassifierManager(applicationContext)
		val threshold = manager.threshold
		val borderline = manager.borderlineOrNull()
		Log.d(TAG, "scan start comments=${comments.size} primary=${manager.primaryIds()} borderlineReady=${borderline != null} thr=${"%.2f".format(threshold)}")
		var processed = 0
		val flaggedTexts = try { comments.filter { comment ->
			try {
				val primary = manager.classifyPrimary(comment)
				var finalScore = primary.probability
				if (borderline != null && manager.isInBorderlineBand(finalScore)) {
					store.incLlmInvocations()
					val res = borderline.classify(comment)
					Log.d(TAG, "llm used text='${comment.take(40)}' primary=${"%.2f".format(primary.probability)}(${primary.backend}) llm=${"%.2f".format(res.probability)}")
					finalScore = maxOf(finalScore, res.probability)
				}
				// Apply explicit user overrides last: user-hate forces flag, user-safe forces not-flag
				val lc = comment.lowercase()
				val hateOverride = userHate.any { it.isNotBlank() && lc.contains(it) }
				val safeOverride = userSafe.any { it.isNotBlank() && lc.contains(it) }
				var isFlagged = finalScore >= threshold
				var overrideNote = ""
				if (hateOverride) { isFlagged = true; overrideNote = " override=hate"; store.addCalibrationSample("hate") }
				else if (safeOverride) { isFlagged = false; overrideNote = " override=safe"; store.addCalibrationSample("safe") }
				store.appendLog("scan:decision score=${"%.2f".format(finalScore)} backend=${primary.backend} flagged=$isFlagged${overrideNote} text='${comment.take(40)}'")
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
		} } finally { manager.close() }
		// De-duplicate against already flagged and hidden items
		val alreadyFlagged = store.getFlaggedItems().map { it.text }.toSet()
		val alreadyHidden = store.getHiddenItems().map { it.text }.toSet()
		val newFlaggedTexts = flaggedTexts.filter { it !in alreadyFlagged && it !in alreadyHidden }
		if (newFlaggedTexts.isNotEmpty()) {
			val byText = commentData.associateBy { it.text }
			val items = newFlaggedTexts.map { text ->
				val rich = byText[text]
				FlaggedItem(
					text = text,
					sourceUrl = sourceUrl,
					commentId = rich?.commentId,
					authorId = rich?.authorId,
					authorHandle = rich?.authorHandle,
					ownedByMe = rich?.postOwnerId != null && myIgUserId != null && rich.postOwnerId == myIgUserId,
				)
			}
			store.appendFlaggedItems(items)
			items.forEach { item -> item.commentId?.let { cid -> store.setCommentIdForText(item.text, cid) } }
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
