package com.nohate.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStore(context: Context) {
	private val masterKey = MasterKey.Builder(context)
		.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
		.build()

	private val prefs = EncryptedSharedPreferences.create(
		context,
		"nohate_prefs",
		masterKey,
		EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
		EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
	)

	fun getIntervalMinutes(): Int = prefs.getInt(KEY_INTERVAL_MIN, 30)

	fun setIntervalMinutes(value: Int) {
		prefs.edit().putInt(KEY_INTERVAL_MIN, value).apply()
	}

	fun getFlaggedComments(): List<String> {
		val raw = prefs.getString(KEY_FLAGGED, "") ?: ""
		if (raw.isEmpty()) return emptyList()
		return raw.split('\u0001').filter { it.isNotEmpty() }
	}

	fun appendFlaggedComments(newOnes: List<String>) {
		if (newOnes.isEmpty()) return
		val existing = getFlaggedComments()
		val merged = (existing + newOnes).takeLast(500)
		prefs.edit().putString(KEY_FLAGGED, merged.joinToString("\u0001")).apply()
	}

	fun setFlaggedComments(all: List<String>) {
		val trimmed = all.takeLast(500)
		prefs.edit().putString(KEY_FLAGGED, trimmed.joinToString("\u0001")).apply()
	}

	fun removeFlaggedAt(index: Int) {
		val current = getFlaggedComments().toMutableList()
		if (index in current.indices) {
			current.removeAt(index)
			setFlaggedComments(current)
		}
	}

	// V2 flagged items with optional URL
	fun getFlaggedItems(): List<FlaggedItem> {
		val rawV2 = prefs.getString(KEY_FLAGGED_V2, "") ?: ""
		if (rawV2.isNotEmpty()) {
			return rawV2.split('\u0001')
				.filter { it.isNotEmpty() }
				.map { entry ->
					val parts = entry.split('\u0002')
					val text = parts.getOrNull(0) ?: ""
					val url = parts.getOrNull(1)?.ifBlank { null }
					FlaggedItem(text = text, sourceUrl = url)
				}
		}
		// Fallback to legacy
		return getFlaggedComments().map { FlaggedItem(text = it, sourceUrl = null) }
	}

	fun setFlaggedItems(items: List<FlaggedItem>) {
		val trimmed = items.takeLast(500)
		val serialized = trimmed.joinToString("\u0001") { item ->
			val safeText = item.text.replace('\u0001', ' ').replace('\u0002', ' ')
			val safeUrl = (item.sourceUrl ?: "").replace('\u0001', ' ').replace('\u0002', ' ')
			"${safeText}\u0002${safeUrl}"
		}
		prefs.edit().putString(KEY_FLAGGED_V2, serialized).apply()
	}

	fun appendFlaggedItems(newOnes: List<FlaggedItem>) {
		if (newOnes.isEmpty()) return
		val existing = getFlaggedItems()
		setFlaggedItems((existing + newOnes).takeLast(500))
	}

	fun removeFlaggedItemAt(index: Int) {
		val current = getFlaggedItems().toMutableList()
		if (index in current.indices) {
			current.removeAt(index)
			setFlaggedItems(current)
		}
	}

	fun correctFalsePositive(index: Int) {
		val items = getFlaggedItems().toMutableList()
		if (index !in items.indices) return
		val text = items[index].text
		items.removeAt(index)
		setFlaggedItems(items)
		addUserSafePhrase(text)
		incFalsePositive()
	}

	// Hidden items
	fun getHiddenItems(): List<FlaggedItem> {
		val raw = prefs.getString(KEY_HIDDEN_V2, "") ?: ""
		if (raw.isEmpty()) return emptyList()
		return raw.split('\u0001').filter { it.isNotEmpty() }.map { entry ->
			val p = entry.split('\u0002')
			val text = p.getOrNull(0) ?: ""
			val url = p.getOrNull(1)?.ifBlank { null }
			FlaggedItem(text, url)
		}
	}

	fun setHiddenItems(items: List<FlaggedItem>) {
		val serialized = items.takeLast(500).joinToString("\u0001") { item ->
			val safeText = item.text.replace('\u0001', ' ').replace('\u0002', ' ')
			val safeUrl = (item.sourceUrl ?: "").replace('\u0001', ' ').replace('\u0002', ' ')
			"${safeText}\u0002${safeUrl}"
		}
		prefs.edit().putString(KEY_HIDDEN_V2, serialized).apply()
	}

	fun appendHiddenItems(newOnes: List<FlaggedItem>) {
		if (newOnes.isEmpty()) return
		val merged = (getHiddenItems() + newOnes).takeLast(500)
		setHiddenItems(merged)
	}

	fun removeHiddenItemAt(index: Int) {
		val current = getHiddenItems().toMutableList()
		if (index in current.indices) {
			current.removeAt(index)
			setHiddenItems(current)
		}
	}

	fun hideFlaggedItemAt(index: Int) {
		val items = getFlaggedItems().toMutableList()
		if (index !in items.indices) return
		val item = items.removeAt(index)
		setFlaggedItems(items)
		appendHiddenItems(listOf(item))
		incHidden()
	}

	fun unhideHiddenItemAt(index: Int) {
		val hidden = getHiddenItems().toMutableList()
		if (index !in hidden.indices) return
		val item = hidden.removeAt(index)
		setHiddenItems(hidden)
		appendFlaggedItems(listOf(item))
	}

	// Last scan stats
	fun setLastScan(tsMillis: Long, total: Int, flagged: Int) {
		prefs.edit()
			.putLong(KEY_LAST_SCAN_AT, tsMillis)
			.putInt(KEY_LAST_SCAN_TOTAL, total)
			.putInt(KEY_LAST_SCAN_FLAGGED, flagged)
			.apply()
	}
	fun getLastScanAt(): Long = prefs.getLong(KEY_LAST_SCAN_AT, 0L)
	fun getLastScanTotal(): Int = prefs.getInt(KEY_LAST_SCAN_TOTAL, 0)
	fun getLastScanFlagged(): Int = prefs.getInt(KEY_LAST_SCAN_FLAGGED, 0)

	// Scan history
	fun appendScanHistory(tsMillis: Long, total: Int, flagged: Int) {
		val entry = listOf(tsMillis.toString(), total.toString(), flagged.toString()).joinToString("\u0002")
		val existing = prefs.getString(KEY_SCAN_HISTORY, "") ?: ""
		val parts = if (existing.isEmpty()) emptyList() else existing.split('\u0001')
		val merged = (parts + entry).takeLast(200).joinToString("\u0001")
		prefs.edit().putString(KEY_SCAN_HISTORY, merged).apply()
	}

	fun getScanHistory(): List<ScanStat> {
		val raw = prefs.getString(KEY_SCAN_HISTORY, "") ?: ""
		if (raw.isEmpty()) return emptyList()
		return raw.split('\u0001').mapNotNull { rec ->
			val p = rec.split('\u0002')
			val ts = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
			val total = p.getOrNull(1)?.toIntOrNull() ?: 0
			val flagged = p.getOrNull(2)?.toIntOrNull() ?: 0
			ScanStat(ts, total, flagged)
		}
	}

	// Metrics counters
	private fun inc(key: String) { prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply() }
	fun incFalsePositive() = inc(KEY_METRIC_FALSE_POS)
	fun incHidden() = inc(KEY_METRIC_HIDDEN)
	fun incDeleted() = inc(KEY_METRIC_DELETED)
	fun incReported() = inc(KEY_METRIC_REPORTED)
	fun incLlmInvocations() = inc(KEY_METRIC_LLM_INVOCATIONS)
	fun incTrainedHate() = inc(KEY_METRIC_TRAIN_HATE)
	fun incTrainedSafe() = inc(KEY_METRIC_TRAIN_SAFE)

	fun getMetricFalsePositive(): Int = prefs.getInt(KEY_METRIC_FALSE_POS, 0)
	fun getMetricHidden(): Int = prefs.getInt(KEY_METRIC_HIDDEN, 0)
	fun getMetricDeleted(): Int = prefs.getInt(KEY_METRIC_DELETED, 0)
	fun getMetricReported(): Int = prefs.getInt(KEY_METRIC_REPORTED, 0)
	fun getMetricLlmInvocations(): Int = prefs.getInt(KEY_METRIC_LLM_INVOCATIONS, 0)
	fun getMetricTrainedHate(): Int = prefs.getInt(KEY_METRIC_TRAIN_HATE, 0)
	fun getMetricTrainedSafe(): Int = prefs.getInt(KEY_METRIC_TRAIN_SAFE, 0)

	fun setFeatureEnabled(featureKey: String, enabled: Boolean) {
		prefs.edit().putBoolean("feature_" + featureKey, enabled).apply()
	}

	fun isFeatureEnabled(featureKey: String): Boolean =
		prefs.getBoolean("feature_" + featureKey, false)

	fun setOAuthToken(provider: String, token: String) {
		prefs.edit().putString("oauth_" + provider, token).apply()
	}

	fun getOAuthToken(provider: String): String? = prefs.getString("oauth_" + provider, null)

	fun setSessionCookies(provider: String, cookies: String) {
		prefs.edit().putString("cookies_" + provider, cookies).apply()
	}

	fun getSessionCookies(provider: String): String? = prefs.getString("cookies_" + provider, null)

	fun clearProvider(provider: String) {
		prefs.edit()
			.remove("oauth_" + provider)
			.remove("cookies_" + provider)
			.apply()
	}

	fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)
	fun setOnboardingComplete(value: Boolean) { prefs.edit().putBoolean(KEY_ONBOARDED, value).apply() }

	// User training lexicon (encrypted)
	fun getUserHatePhrases(): List<String> {
		val raw = prefs.getString(KEY_USER_HATE, "") ?: ""
		return if (raw.isEmpty()) emptyList() else raw.split('\u0001').filter { it.isNotBlank() }
	}

	fun addUserHatePhrase(text: String) {
		val cleaned = text.trim().lowercase()
		if (cleaned.isEmpty()) return
		val merged = (getUserHatePhrases() + cleaned).distinct().takeLast(1000)
		prefs.edit().putString(KEY_USER_HATE, merged.joinToString("\u0001")).apply()
		incTrainedHate()
	}

	fun getUserSafePhrases(): List<String> {
		val raw = prefs.getString(KEY_USER_SAFE, "") ?: ""
		return if (raw.isEmpty()) emptyList() else raw.split('\u0001').filter { it.isNotBlank() }
	}

	fun addUserSafePhrase(text: String) {
		val cleaned = text.trim().lowercase()
		if (cleaned.isEmpty()) return
		val merged = (getUserSafePhrases() + cleaned).distinct().takeLast(1000)
		prefs.edit().putString(KEY_USER_SAFE, merged.joinToString("\u0001")).apply()
		incTrainedSafe()
	}

	fun setUseQuantizedModel(enabled: Boolean) {
		prefs.edit().putBoolean(KEY_USE_QUANT, enabled).apply()
	}

	fun isUseQuantizedModel(): Boolean = prefs.getBoolean(KEY_USE_QUANT, false)

	fun setUseLlm(enabled: Boolean) {
		prefs.edit().putBoolean(KEY_USE_LLM, enabled).apply()
	}

	fun isUseLlm(): Boolean = prefs.getBoolean(KEY_USE_LLM, false)

	fun getFlagThreshold(): Float = prefs.getFloat(KEY_FLAG_THRESHOLD, 0.8f).coerceIn(0.5f, 0.95f)
	fun setFlagThreshold(value: Float) {
		val v = value.coerceIn(0.5f, 0.95f)
		prefs.edit().putFloat(KEY_FLAG_THRESHOLD, v).apply()
	}

	// Console logs (encrypted, local)
	fun appendLog(line: String) {
		val ts = System.currentTimeMillis()
		val entry = "${ts}:${line.replace('\n', ' ')}"
		val existing = prefs.getString(KEY_LOGS, "") ?: ""
		val parts = if (existing.isEmpty()) emptyList() else existing.split('\u0001')
		val merged = (parts + entry).takeLast(1000).joinToString("\u0001")
		prefs.edit().putString(KEY_LOGS, merged).apply()
	}

	fun getLogs(): List<String> {
		val raw = prefs.getString(KEY_LOGS, "") ?: ""
		if (raw.isEmpty()) return emptyList()
		return raw.split('\u0001').filter { it.isNotEmpty() }
	}

	fun clearLogs() { prefs.edit().remove(KEY_LOGS).apply() }

	companion object {
		private const val KEY_INTERVAL_MIN = "interval_min"
		private const val KEY_FLAGGED = "flagged_comments" // legacy text-only
		private const val KEY_FLAGGED_V2 = "flagged_items_v2" // text + url
		private const val KEY_HIDDEN_V2 = "hidden_items_v2"
		private const val KEY_ONBOARDED = "onboarding_complete"
		private const val KEY_USER_HATE = "user_hate_phrases"
		private const val KEY_USER_SAFE = "user_safe_phrases"
		private const val KEY_USE_QUANT = "use_quantized_model"
		private const val KEY_USE_LLM = "use_llm"
		private const val KEY_FLAG_THRESHOLD = "flag_threshold"
		private const val KEY_LOGS = "console_logs"
		private const val KEY_LAST_SCAN_AT = "last_scan_at"
		private const val KEY_LAST_SCAN_TOTAL = "last_scan_total"
		private const val KEY_LAST_SCAN_FLAGGED = "last_scan_flagged"
		private const val KEY_SCAN_HISTORY = "scan_history"
		private const val KEY_METRIC_FALSE_POS = "metric_false_positive"
		private const val KEY_METRIC_HIDDEN = "metric_hidden"
		private const val KEY_METRIC_DELETED = "metric_deleted"
		private const val KEY_METRIC_REPORTED = "metric_reported"
		private const val KEY_METRIC_LLM_INVOCATIONS = "metric_llm_invocations"
		private const val KEY_METRIC_TRAIN_HATE = "metric_train_hate"
		private const val KEY_METRIC_TRAIN_SAFE = "metric_train_safe"
	}
}
