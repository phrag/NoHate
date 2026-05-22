package com.nohate.app.bench

import android.content.Context

/**
 * One labeled sample from the bundled evaluation CSV.
 * `label = true` means hate.
 */
data class EvalSample(val text: String, val isHate: Boolean)

/**
 * Loads the on-device benchmark fixtures.
 *
 * - `evalSamples()` parses `assets/bench/eval.csv` (CSV header: `label,text`).
 *   `label` is `1` for hate, `0` for non-hate. Lines starting with `#` are
 *   treated as comments and skipped.
 * - `latencyCorpus()` returns a deterministic 200-string corpus used for the
 *   latency / throughput probes — re-derived from the eval set so the
 *   benchmark works even if the eval CSV is a placeholder.
 */
object BenchSuite {
	private const val EVAL_ASSET = "bench/eval.csv"

	fun evalSamples(context: Context): List<EvalSample> = try {
		context.assets.open(EVAL_ASSET).use { input ->
			input.bufferedReader().useLines { lines ->
				lines.mapNotNull { parseRow(it) }.toList()
			}
		}
	} catch (_: Throwable) {
		emptyList()
	}

	fun latencyCorpus(context: Context, size: Int = 200): List<String> {
		val eval = evalSamples(context).map { it.text }
		if (eval.isEmpty()) return synthetic(size)
		return List(size) { eval[it % eval.size] }
	}

	private fun synthetic(size: Int): List<String> {
		val seeds = listOf(
			"This is a perfectly normal comment.",
			"You are awful and I hate this.",
			"Great post, thanks for sharing!",
			"Get out of here, you stupid trash.",
			"Looking forward to more like this :)",
		)
		return List(size) { seeds[it % seeds.size] }
	}

	private fun parseRow(line: String): EvalSample? {
		val trimmed = line.trim()
		if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
		if (trimmed.equals("label,text", ignoreCase = true)) return null
		val commaIdx = trimmed.indexOf(',')
		if (commaIdx <= 0) return null
		val labelRaw = trimmed.substring(0, commaIdx).trim()
		var text = trimmed.substring(commaIdx + 1).trim()
		if (text.startsWith("\"") && text.endsWith("\"") && text.length >= 2) {
			text = text.substring(1, text.length - 1).replace("\"\"", "\"")
		}
		val isHate = when (labelRaw) {
			"1", "true", "hate" -> true
			"0", "false", "not_hate", "non-hate" -> false
			else -> return null
		}
		if (text.isEmpty()) return null
		return EvalSample(text, isHate)
	}
}
