package com.nohate.app.platform

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PostImporter {
	private const val TAG = "PostImporter"

	fun fetchPublicComments(postUrl: String, limit: Int = 50, cookies: String? = null): List<String> {
		return fetchPublicCommentsRich(postUrl, limit, cookies).map { it.text }
	}

	fun fetchPublicCommentsRich(postUrl: String, limit: Int = 50, cookies: String? = null): List<CommentData> {
		return try {
			val url = postUrl.trim()
			val short = extractShortcode(url)
			if (short == null) {
				val html = httpGet(url, cookies)
				val fromHtml = if (html != null) extractFromHtml(html, limit) else emptyList()
				return fromHtml.take(limit).map { CommentData(text = it) }
			}
			val paged = fetchPaginatedRich(short, limit, cookies)
			if (paged.isNotEmpty()) return paged
			val jsonA = httpGet("https://www.instagram.com/p/${short}/?__a=1&__d=dis", cookies)
			val fromJson = if (!jsonA.isNullOrEmpty()) extractFromJsonRich(jsonA, limit) else emptyList()
			if (fromJson.isNotEmpty()) return fromJson.take(limit)
			val html = httpGet("https://www.instagram.com/p/${short}/", cookies)
			val fromHtml = if (!html.isNullOrEmpty()) extractFromHtml(html, limit) else emptyList()
			fromHtml.take(limit).map { CommentData(text = it) }
		} catch (t: Throwable) {
			Log.e(TAG, "import error", t)
			emptyList()
		}
	}

	private fun httpGet(urlStr: String, cookies: String? = null): String? {
		return try {
			val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
				connectTimeout = 15000
				readTimeout = 30000
				requestMethod = "GET"
				instanceFollowRedirects = true
				setRequestProperty("User-Agent", "Mozilla/5.0 (Android) NoHate/1.0")
				setRequestProperty("Accept", "text/html,application/json")
				setRequestProperty("Accept-Language", "en-US,en;q=0.9")
				if (!cookies.isNullOrBlank()) {
					setRequestProperty("Cookie", cookies)
					setRequestProperty("Referer", "https://www.instagram.com/")
				}
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

	private fun extractFromJson(jsonStr: String, limit: Int): List<String> =
		extractFromJsonRich(jsonStr, limit).map { it.text }

	private fun extractFromJsonRich(jsonStr: String, limit: Int): List<CommentData> {
		return try {
			val out = mutableListOf<CommentData>()
			val root = JSONObject(jsonStr)
			val graphql = root.optJSONObject("graphql") ?: root.optJSONObject("data")
			val media = graphql?.optJSONObject("shortcode_media")
			val postOwnerId = media?.optJSONObject("owner")?.optString("id")?.ifBlank { null }
			val comments = media?.optJSONObject("edge_media_to_parent_comment")?.optJSONArray("edges")
			if (comments != null) {
				for (i in 0 until comments.length()) {
					val node = comments.getJSONObject(i).optJSONObject("node") ?: continue
					val text = node.optString("text")
					if (text.isBlank()) continue
					val owner = node.optJSONObject("owner")
					out.add(
						CommentData(
							text = text,
							commentId = node.optString("id").ifBlank { null },
							authorId = owner?.optString("id")?.ifBlank { null },
							authorHandle = owner?.optString("username")?.ifBlank { null },
							postOwnerId = postOwnerId,
						)
					)
					if (out.size >= limit) break
				}
			}
			out
		} catch (_: Throwable) { emptyList() }
	}

	private fun fetchPaginated(shortcode: String, limit: Int, cookies: String? = null): List<String> =
		fetchPaginatedRich(shortcode, limit, cookies).map { it.text }

	private fun fetchPaginatedRich(shortcode: String, limit: Int, cookies: String? = null): List<CommentData> {
		val out = mutableListOf<CommentData>()
		var endCursor: String? = null
		var hasNext = true
		while (hasNext && out.size < limit) {
			val base = "https://www.instagram.com/graphql/query/?query_hash=97b41c52301f77ce508f55e66d17620e&variables="
			val variables = JSONObject(mapOf(
				"shortcode" to shortcode,
				"include_reel" to true,
				"first" to 50,
				"after" to (endCursor ?: JSONObject.NULL)
			)).toString()
			val url = base + java.net.URLEncoder.encode(variables, "UTF-8")
			val json = httpGet(url, cookies) ?: break
			try {
				val root = JSONObject(json)
				val media = root.optJSONObject("data")?.optJSONObject("shortcode_media")
				val postOwnerId = media?.optJSONObject("owner")?.optString("id")?.ifBlank { null }
				val parent = media?.optJSONObject("edge_media_to_parent_comment")
				val edges = parent?.optJSONArray("edges")
				val pageInfo = parent?.optJSONObject("page_info")
				if (edges != null) {
					for (i in 0 until edges.length()) {
						val node = edges.getJSONObject(i).optJSONObject("node") ?: continue
						val text = node.optString("text")
						if (text.isBlank()) continue
						val owner = node.optJSONObject("owner")
						out.add(
							CommentData(
								text = text,
								commentId = node.optString("id").ifBlank { null },
								authorId = owner?.optString("id")?.ifBlank { null },
								authorHandle = owner?.optString("username")?.ifBlank { null },
								postOwnerId = postOwnerId,
							)
						)
						if (out.size >= limit) break
					}
				}
				hasNext = pageInfo?.optBoolean("has_next_page") == true
				endCursor = pageInfo?.optString("end_cursor")
			} catch (_: Throwable) { break }
		}
		return out
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
