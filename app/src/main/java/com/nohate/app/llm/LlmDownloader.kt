package com.nohate.app.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object LlmDownloader {
	private const val TAG = "LlmDownloader"

	suspend fun resolveAndDownload(
		context: Context,
		repoId: String = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
		quantSuffix: String = "Q4_K_M"
	): Boolean = withContext(Dispatchers.IO) {
		val api = "https://huggingface.co/api/models/" + repoId
		Log.d(TAG, "Resolving model via $api")
		val json = httpGet(api)
		if (json == null) { Log.e(TAG, "Failed to fetch model API") ; return@withContext false }
		val files = JSONObject(json).optJSONArray("siblings")
		if (files == null) { Log.e(TAG, "No 'siblings' in API response") ; return@withContext false }
		var fileName: String? = null
		for (i in 0 until files.length()) {
			val name = files.getJSONObject(i).optString("rfilename")
			if (name.endsWith(".$quantSuffix.gguf", ignoreCase = true)) { fileName = name; break }
		}
		if (fileName == null) { Log.e(TAG, "No file ending with .$quantSuffix.gguf found") ; return@withContext false }
		val url = "https://huggingface.co/" + repoId + "/resolve/main/" + fileName
		Log.d(TAG, "Resolved download URL: $url")
		return@withContext downloadModel(context, url, "")
	}

	suspend fun downloadModel(
		context: Context,
		url: String,
		expectedSha256: String,
		onProgress: ((downloaded: Long, total: Long) -> Unit)? = null
	): Boolean = withContext(Dispatchers.IO) {
		try {
			val dir = File(context.filesDir, "llm").apply { mkdirs() }
			val tmp = File(dir, "model.tmp")
			var existing = if (tmp.exists()) tmp.length() else 0L
			var downloaded: Long
			val conn = (URL(url).openConnection() as HttpURLConnection).apply {
				connectTimeout = 30_000
				readTimeout = 600_000
				instanceFollowRedirects = true
				requestMethod = "GET"
				setRequestProperty("User-Agent", "NoHate/1.0 (Android)")
				setRequestProperty("Accept", "application/octet-stream")
				setRequestProperty("Accept-Encoding", "identity")
				if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
				connect()
			}
			val response = conn.responseCode
			val contentRange = conn.getHeaderField("Content-Range")
			val total = when {
				response == 206 && contentRange != null -> {
					val slash = contentRange.lastIndexOf('/')
					contentRange.substring(slash + 1).toLongOrNull() ?: -1L
				}
				else -> conn.contentLengthLong
			}
			Log.d(TAG, "HTTP $response total=$total existing=$existing")
			val append = response == 206 && existing > 0
			if (!append) existing = 0L
			downloaded = existing
			if (response !in 200..299) { Log.e(TAG, "Bad response code: $response"); return@withContext false }
			conn.inputStream.use { input ->
				tmp.outputStream().use { out ->
					if (append) out.channel.position(existing)
					copyStreamCount(input, out) { chunk ->
						downloaded += chunk
						onProgress?.invoke(downloaded, if (total > 0) total else -1L)
					}
				}
			}
			Log.d(TAG, "Downloaded bytes=$downloaded")
			if (downloaded <= 0) { Log.e(TAG, "Zero bytes downloaded"); tmp.delete(); return@withContext false }
			if (total > 0 && downloaded != total) { Log.e(TAG, "Length mismatch: downloaded=$downloaded total=$total"); tmp.delete(); return@withContext false }
			val actual = sha256(tmp.inputStream())
			val exp = expectedSha256.trim()
			if (exp.isNotEmpty() && !actual.equals(exp, ignoreCase = true)) { Log.e(TAG, "Checksum mismatch"); tmp.delete(); return@withContext false }
			val model = File(dir, "model.gguf").apply { if (exists()) delete() }
			if (!tmp.renameTo(model)) { Log.e(TAG, "Rename failed"); tmp.delete(); return@withContext false }
			File(dir, "model.sha256").writeText(if (exp.isNotEmpty()) exp else actual)
			Log.d(TAG, "Model stored at ${model.absolutePath}")
			true
		} catch (t: Throwable) {
			Log.e(TAG, "Download error", t)
			false
		}
	}

	private fun httpGet(urlStr: String): String? {
		return try {
			val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
				connectTimeout = 15_000
				readTimeout = 30_000
				requestMethod = "GET"
				setRequestProperty("User-Agent", "NoHate/1.0 (Android)")
				setRequestProperty("Accept", "application/json")
				connect()
			}
			if (conn.responseCode !in 200..299) null else conn.inputStream.use { it.reader().readText() }
		} catch (_: Throwable) { null }
	}

	private fun copyStreamCount(input: InputStream, out: java.io.OutputStream, onChunk: (Int) -> Unit) {
		val buf = ByteArray(64 * 1024)
		while (true) {
			val r = input.read(buf)
			if (r <= 0) break
			out.write(buf, 0, r)
			onChunk(r)
		}
	}

	private fun sha256(ins: InputStream): String {
		val md = MessageDigest.getInstance("SHA-256")
		val buf = ByteArray(8192)
		ins.use { stream ->
			while (true) {
				val r = stream.read(buf)
				if (r <= 0) break
				md.update(buf, 0, r)
			}
		}
		return md.digest().joinToString("") { b -> "%02x".format(b) }
	}
}
