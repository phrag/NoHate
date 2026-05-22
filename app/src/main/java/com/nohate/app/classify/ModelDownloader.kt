package com.nohate.app.classify

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Generalized model downloader. Mirrors the resume + checksum logic in
 * [com.nohate.app.llm.LlmDownloader] but is parameterized by a [ModelEntry]
 * from the registry rather than the hardcoded TinyLlama path.
 *
 * Downloaded artefacts live under `filesDir/models/<id>/model.<ext>` with a
 * sibling `model.sha256` file recording the verified digest. The legacy
 * `LlmDownloader` continues to manage the `filesDir/llm/` directory for
 * backwards compatibility.
 */
object ModelDownloader {
	private const val TAG = "ModelDownloader"

	fun localFile(context: Context, entry: ModelEntry): File {
		val ext = when (entry.kind) {
			"onnx" -> "onnx"
			"tflite" -> "tflite"
			"llm" -> "gguf"
			else -> "bin"
		}
		val dir = File(context.filesDir, "models/${entry.id}").apply { mkdirs() }
		return File(dir, "model.$ext")
	}

	fun isPresent(context: Context, entry: ModelEntry): Boolean =
		localFile(context, entry).exists()

	suspend fun download(
		context: Context,
		entry: ModelEntry,
		onProgress: ((downloaded: Long, total: Long) -> Unit)? = null,
	): Boolean = withContext(Dispatchers.IO) {
		val url = entry.url ?: return@withContext false
		try {
			val target = localFile(context, entry)
			val tmp = File(target.parentFile, target.name + ".tmp")
			var existing = if (tmp.exists()) tmp.length() else 0L
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
			val append = response == 206 && existing > 0
			if (!append) existing = 0L
			if (response !in 200..299) {
				Log.e(TAG, "HTTP $response for ${entry.id}")
				return@withContext false
			}
			var downloaded = existing
			conn.inputStream.use { input ->
				tmp.outputStream().use { out ->
					if (append) out.channel.position(existing)
					copyStreamCount(input, out) { chunk ->
						downloaded += chunk
						onProgress?.invoke(downloaded, if (total > 0) total else -1L)
					}
				}
			}
			if (downloaded <= 0) { tmp.delete(); return@withContext false }
			if (total > 0 && downloaded != total) { tmp.delete(); return@withContext false }
			val actual = sha256(tmp.inputStream())
			val expected = entry.sha256.trim()
			if (expected.isNotEmpty() && !actual.equals(expected, ignoreCase = true)) {
				Log.e(TAG, "Checksum mismatch for ${entry.id}")
				tmp.delete()
				return@withContext false
			}
			if (target.exists()) target.delete()
			if (!tmp.renameTo(target)) { tmp.delete(); return@withContext false }
			File(target.parentFile, "model.sha256")
				.writeText(if (expected.isNotEmpty()) expected else actual)
			true
		} catch (t: Throwable) {
			Log.e(TAG, "download error for ${entry.id}", t)
			false
		}
	}

	fun remove(context: Context, entry: ModelEntry): Boolean {
		val f = localFile(context, entry)
		val sha = File(f.parentFile, "model.sha256")
		val ok = (!f.exists() || f.delete()) && (!sha.exists() || sha.delete())
		f.parentFile?.takeIf { it.exists() && it.listFiles().isNullOrEmpty() }?.delete()
		return ok
	}

	private fun copyStreamCount(input: InputStream, out: OutputStream, onChunk: (Int) -> Unit) {
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
		ins.use { s ->
			while (true) {
				val r = s.read(buf)
				if (r <= 0) break
				md.update(buf, 0, r)
			}
		}
		return md.digest().joinToString("") { b -> "%02x".format(b) }
	}
}
