package com.nohate.app.platform

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PostImporter {
	private const val TAG = "PostImporter"

	fun fetchPublicComments(postUrl: String, limit: Int = 50): List<String> {
		return try {
			val url = postUrl.trim()
			// Try direct HTML first
			val html = httpGet(url)
			val fromHtml = if (html != null) extractFromHtml(html, limit) else emptyList()
			if (fromHtml.isNotEmpty()) return fromHtml
			// Try JSON endpoint using shortcode
			val short = extractShortcode(url) ?: return emptyList()
			val jsonUrl = "https://www.instagram.com/p/${short}/?__a=1&__d=dis"
			val jsonStr = httpGet(jsonUrl)
			if (jsonStr != null) extractFromJson(jsonStr, limit) else emptyList()
		} catch (t: Throwable) {
			Log.e(TAG, "import error", t)
			emptyList()
		}
	}

	private fun httpGet(urlStr: String): String? {
		return try {
			val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
				connectTimeout = 15000
				readTimeout = 30000
				requestMethod = "GET"
				instanceFollowRedirects = true
				setRequestProperty("User-Agent", "Mozilla/5.0 (Android) NoHate/1.0")
				setRequestProperty("Accept", "text/html,application/json")
				connect()
			}
			if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.reader().readText() }
		} catch (_: Throwable) { null }
	}

	private fun extractFromHtml(html: String, limit: Int): List<String> {
		// Heuristic: Instagram pages often embed JSON blobs; try to find comment nodes
		val results = mutableListOf<String>()
		// Look for occurrences of \"text\":\"...\" in JSON sections
		val regex = Regex("\\\"text\\\":\\\"(.*?)\\\"", RegexOption.DOT_MATCHES_ALL)
		for (m in regex.findAll(html)) {
			val raw = m.groupValues.getOrNull(1) ?: continue
			val text = unescapeJson(raw).trim()
			if (text.isNotEmpty()) results.add(text)
			if (results.size >= limit) break
		}
		return results
	}

	private fun extractFromJson(jsonStr: String, limit: Int): List<String> {
		return try {
			val out = mutableListOf<String>()
			val root = JSONObject(jsonStr)
			// Walk common shapes
			val graphql = root.optJSONObject("graphql")
			val media = graphql?.optJSONObject("shortcode_media")
			val comments = media?.optJSONObject("edge_media_to_parent_comment")?.optJSONArray("edges")
			if (comments != null) {
				for (i in 0 until comments.length()) {
					val node = comments.getJSONObject(i).optJSONObject("node")
					val text = node?.optString("text") ?: ""
					if (text.isNotBlank()) out.add(text)
					if (out.size >= limit) break
				}
			}
			out
		} catch (_: Throwable) { emptyList() }
	}

	private fun extractShortcode(url: String): String? {
		val r = Regex("/(p|reel)/([A-Za-z0-9_-]+)/?")
		return r.find(url)?.groupValues?.getOrNull(2)
	}

	private fun unescapeJson(s: String): String {
		return s
			.replace("\\\"", "\"")
			.replace("\\n", "\n")
			.replace("\\r", "\r")
			.replace("\\t", "\t")
			.replace(Regex("\\\\u([0-9a-fA-F]{4})")) { m ->
				m.groupValues.getOrNull(1)?.toInt(16)?.toChar()?.toString() ?: m.value
			}
	}
}
