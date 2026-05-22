package com.nohate.app.classify

import android.content.Context
import org.json.JSONObject

/**
 * Parses `assets/models/registry.json` (see [docs/MODELS.md]) into a typed list.
 *
 * Phase 2: only the `onnx` kind is honoured here; other kinds are still
 * hard-coded in [ClassifierRegistry].
 */
data class ModelEntry(
	val id: String,
	val displayName: String,
	val kind: String,
	val primary: Boolean,
	val bundled: Boolean,
	val asset: String?,
	val tokenizerAsset: String?,
	val url: String?,
	val sha256: String,
	val sizeBytes: Long,
	val estPeakMemMb: Int,
	val languages: List<String>,
	val license: String,
	val version: String,
	val sourceRepo: String?,
	val tokenizerEmbedded: Boolean,
	val outputKind: OutputKind,
) {
	enum class OutputKind { SIGMOID_SINGLE, SOFTMAX_PAIR, RAW_LOGIT;
		companion object {
			fun parse(raw: String?): OutputKind = when (raw) {
				"sigmoid_single" -> SIGMOID_SINGLE
				"softmax_pair" -> SOFTMAX_PAIR
				"raw_logit" -> RAW_LOGIT
				else -> SIGMOID_SINGLE
			}
		}
	}
}

object ModelRegistry {
	private const val ASSET_PATH = "models/registry.json"

	fun load(context: Context): List<ModelEntry> = try {
		val raw = context.assets.open(ASSET_PATH).use { it.reader().readText() }
		val root = JSONObject(raw)
		val arr = root.optJSONArray("models")
		if (arr == null) emptyList() else buildList {
			for (i in 0 until arr.length()) {
				val o = arr.getJSONObject(i)
				val langs = o.optJSONArray("languages")
					?.let { a -> List(a.length()) { idx -> a.getString(idx) } }
					?: emptyList()
				add(
					ModelEntry(
						id = o.getString("id"),
						displayName = o.optString("displayName", o.getString("id")),
						kind = o.getString("kind"),
						primary = o.optBoolean("primary", false),
						bundled = o.optBoolean("bundled", false),
						asset = o.optString("asset", "").takeIf { it.isNotEmpty() },
						tokenizerAsset = o.optString("tokenizer", "").takeIf { it.isNotEmpty() },
						url = o.optString("url", "").takeIf { it.isNotEmpty() },
						sha256 = o.optString("sha256", ""),
						sizeBytes = o.optLong("sizeBytes", 0L),
						estPeakMemMb = o.optInt("estPeakMemMb", 50),
						languages = langs,
						license = o.optString("license", "UNKNOWN"),
						version = o.optString("version", "0.0.0"),
						sourceRepo = o.optString("sourceRepo", "").takeIf { it.isNotEmpty() },
						tokenizerEmbedded = o.optBoolean("tokenizerEmbedded", true),
						outputKind = ModelEntry.OutputKind.parse(o.optString("outputKind", null)),
					)
				)
			}
		}
	} catch (_: Throwable) {
		emptyList()
	}

	fun onnxModels(context: Context): List<ModelEntry> =
		load(context).filter { it.kind == "onnx" }
}
