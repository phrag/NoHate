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

	companion object {
		private const val KEY_INTERVAL_MIN = "interval_min"
		private const val KEY_FLAGGED = "flagged_comments"
	}
}
