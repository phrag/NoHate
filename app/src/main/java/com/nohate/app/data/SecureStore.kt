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

	companion object {
		private const val KEY_INTERVAL_MIN = "interval_min"
		private const val KEY_FLAGGED = "flagged_comments"
		private const val KEY_ONBOARDED = "onboarding_complete"
		private const val KEY_USER_HATE = "user_hate_phrases"
		private const val KEY_USER_SAFE = "user_safe_phrases"
		private const val KEY_USE_QUANT = "use_quantized_model"
		private const val KEY_USE_LLM = "use_llm"
		private const val KEY_FLAG_THRESHOLD = "flag_threshold"
	}
}
