package com.nohate.app.platform

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class InstagramGraph(private val context: Context, private val accessTokenProvider: () -> String?) {
	fun isAuthorized(): Boolean = !accessTokenProvider().isNullOrBlank()

	fun startLogin(authUrl: String) {
		CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authUrl))
	}

	private fun postForm(urlStr: String, params: Map<String, String>): Boolean {
		return try {
			val data = params.map { (k, v) ->
				URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
			}.joinToString("&")
			val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
				requestMethod = "POST"
				connectTimeout = 15000
				readTimeout = 30000
				doOutput = true
				setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
			}
			conn.outputStream.use { it.write(data.toByteArray(Charsets.UTF_8)) }
			(conn.responseCode in 200..299)
		} catch (_: Throwable) { false }
	}

	fun hideComment(commentId: String, hide: Boolean): Boolean {
		val token = accessTokenProvider() ?: return false
		val url = "https://graph.facebook.com/v20.0/$commentId"
		return postForm(url, mapOf("hide" to hide.toString(), "access_token" to token))
	}

	fun deleteComment(commentId: String): Boolean {
		val token = accessTokenProvider() ?: return false
		return try {
			val url = "https://graph.facebook.com/v20.0/$commentId?access_token=$token"
			val conn = (URL(url).openConnection() as HttpURLConnection).apply {
				requestMethod = "DELETE"
				connectTimeout = 15000
				readTimeout = 30000
			}
			(conn.responseCode in 200..299)
		} catch (_: Throwable) { false }
	}

	fun blockUser(igUserId: String, targetUserId: String): Boolean {
		val token = accessTokenProvider() ?: return false
		val url = "https://graph.facebook.com/v20.0/$igUserId/blocked"
		return postForm(url, mapOf("user" to targetUserId, "access_token" to token))
	}
}
