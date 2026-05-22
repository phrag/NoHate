package com.nohate.app.classify

import android.content.Context
import com.nohate.app.NativeClassifier
import com.nohate.app.data.SecureStore

/**
 * Lazy factory for every classifier backend the app knows about.
 *
 * Backends are constructed on first use and cached for the lifetime of the
 * registry instance. Construction can be expensive (memory-mapped model files,
 * native runtime init, llama.cpp warmup), so the registry should be scoped to
 * a single ScanWorker invocation and `close()`-d when done.
 */
class ClassifierRegistry(private val context: Context) {
	private val store by lazy { SecureStore(context) }
	private val cache = mutableMapOf<String, Classifier>()
	private val onnxEntries: Map<String, ModelEntry> by lazy {
		ModelRegistry.onnxModels(context).associateBy { it.id }
	}

	/**
	 * Ids of every backend the app is currently willing to run, in priority order:
	 *   ready ONNX primaries, rules core, optional TFLite stub, optional LLM.
	 */
	fun available(): List<String> = buildList {
		addAll(readyOnnxIds())
		if (NativeClassifier.isLibraryLoaded) add(RulesClassifier.ID)
		if (store.isUseQuantizedModel()) add(LegacyTfliteClassifier.ID)
		if (store.isUseLlm()) add(LlmClassifier.ID)
	}

	fun get(id: String): Classifier? {
		cache[id]?.let { return it }
		val created = create(id) ?: return null
		cache[id] = created
		return created
	}

	private fun create(id: String): Classifier? {
		if (id in onnxEntries) {
			val entry = onnxEntries.getValue(id)
			return OnnxClassifier(context, entry).takeIf { it.isReady() }
		}
		return when (id) {
			RulesClassifier.ID -> RulesClassifier(
				userHate = { store.getUserHatePhrases() },
				userSafe = { store.getUserSafePhrases() },
			)
			LegacyTfliteClassifier.ID -> LegacyTfliteClassifier(context)
			LlmClassifier.ID -> LlmClassifier(context).takeIf { it.isReady() }
			else -> null
		}
	}

	/**
	 * ONNX models whose artefact is on disk right now. Bundled models are
	 * considered ready as long as the asset path resolves at construction;
	 * downloaded models need a local file under `filesDir/models/<id>/`.
	 */
	private fun readyOnnxIds(): List<String> = onnxEntries.values
		.filter { it.primary }
		.filter { entry ->
			if (entry.bundled && entry.asset != null) assetExists(entry.asset)
			else ModelDownloader.isPresent(context, entry)
		}
		.map { it.id }

	private fun assetExists(name: String): Boolean = try {
		context.assets.open(name).use { true }
	} catch (_: Throwable) { false }

	fun close() {
		cache.values.forEach { runCatching { it.close() } }
		cache.clear()
	}
}
