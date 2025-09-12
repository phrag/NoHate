package com.nohate.app.llm

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

interface LlmEngine {
	fun isReady(): Boolean
	fun modelPresent(): Boolean
	fun classify(text: String, prompt: String): Result
	data class Result(val label: String, val score: Float)
}

class LlamaEngine(private val context: Context) : LlmEngine {
	private var ready: Boolean = false
	private var modelPath: String? = null

	init {
		try {
			modelPath = ensureModel()
			ready = modelPath?.let { LlamaBridge.loadModel(it) } == true
			if (ready) {
				classify("warmup", PROMPT)
			}
		} catch (_: Throwable) { ready = false }
	}

	override fun isReady(): Boolean = ready
	override fun modelPresent(): Boolean = File(context.filesDir, "llm/model.gguf").exists()

	override fun classify(text: String, prompt: String): LlmEngine.Result {
		return try {
			val json = LlamaBridge.classify(text, prompt)
			val obj = JSONObject(json)
			val label = obj.optString("label", "unknown")
			val score = obj.optDouble("score", 0.0).toFloat().coerceIn(0f, 1f)
			LlmEngine.Result(label, score)
		} catch (_: Throwable) {
			LlmEngine.Result("unknown", 0f)
		}
	}

	private fun ensureModel(): String? {
		val dir = File(context.filesDir, "llm").apply { mkdirs() }
		val outFile = File(dir, "model.gguf")
		val shaFile = File(dir, "model.sha256")
		if (!outFile.exists()) return null
		// Optional: verify checksum if present
		if (shaFile.exists()) {
			val expected = shaFile.readText().trim()
			if (expected.isNotEmpty()) {
				val actual = sha256(outFile.inputStream())
				if (!actual.equals(expected, ignoreCase = true)) return null
			}
		}
		return outFile.absolutePath
	}

	private fun sha256(ins: InputStream): String {
		val md = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(8192)
		ins.use { s ->
			while (true) { val r = s.read(buf); if (r <= 0) break; md.update(buf, 0, r) }
		}
		return md.digest().joinToString("") { b -> "%02x".format(b) }
	}

	companion object {
		const val PROMPT = """
		You are a precise hate-speech classifier. Return one compact JSON line only, no prose.
		Schema: {"label":"hate|not_hate","score":0.0-1.0}
		Guidelines:
		- Label "hate" for demeaning slurs, threats, calls for violence, dehumanization, or attacks on protected groups/traits.
		- Label "not_hate" for neutral or contextless content, sarcasm without direct target, or praise.
		- Be conservative: if uncertain, keep score near 0.5, but do not add text.
		- Consider short comments and slang; avoid over-weighting expletives without a target.
		Examples:
		- "you are scum" -> {"label":"hate","score":0.86}
		- "that song slaps" -> {"label":"not_hate","score":0.03}
		Text: {{text}}
		"""
	}
}
