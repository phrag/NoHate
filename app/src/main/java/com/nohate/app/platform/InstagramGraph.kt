package com.nohate.app.platform

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class InstagramGraph(private val context: Context, private val accessTokenProvider: () -> String?) {
	private val http = OkHttpClient()
	private val json = "application/json; charset=utf-8".toMediaType()

	fun isAuthorized(): Boolean = !accessTokenProvider().isNullOrBlank()

	fun startLogin(authUrl: String) {
		CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authUrl))
	}

	fun hideComment(commentId: String, hide: Boolean): Boolean {
		val token = accessTokenProvider() ?: return false
		val url = "https://graph.facebook.com/v20.0/$commentId"
		val body = JSONObject(mapOf("hide" to hide.toString(), "access_token" to token)).toString().toRequestBody(json)
		val req = Request.Builder().url(url).post(body).build()
		http.newCall(req).execute().use { resp -> return resp.isSuccessful }
	}

	fun deleteComment(commentId: String): Boolean {
		val token = accessTokenProvider() ?: return false
		val url = "https://graph.facebook.com/v20.0/$commentId?access_token=$token"
		val req = Request.Builder().url(url).delete().build()
		http.newCall(req).execute().use { resp -> return resp.isSuccessful }
	}

	fun blockUser(igUserId: String, targetUserId: String): Boolean {
		val token = accessTokenProvider() ?: return false
		val url = "https://graph.facebook.com/v20.0/$igUserId/blocked"
		val body = JSONObject(mapOf("user" to targetUserId, "access_token" to token)).toString().toRequestBody(json)
		val req = Request.Builder().url(url).post(body).build()
		http.newCall(req).execute().use { resp -> return resp.isSuccessful }
	}
}
