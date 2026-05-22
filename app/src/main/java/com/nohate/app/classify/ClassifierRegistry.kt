package com.nohate.app.classify

import android.content.Context
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

	fun available(): List<String> = buildList {
		add(RulesClassifier.ID)
		if (store.isUseQuantizedModel()) add(LegacyTfliteClassifier.ID)
		if (store.isUseLlm()) add(LlmClassifier.ID)
		// Phase 2: ONNX backends discovered from registry.json appended here.
	}

	fun get(id: String): Classifier? = cache.getOrPut(id) {
		create(id) ?: return null
	}

	private fun create(id: String): Classifier? = when (id) {
		RulesClassifier.ID -> RulesClassifier(
			userHate = { store.getUserHatePhrases() },
			userSafe = { store.getUserSafePhrases() },
		)
		LegacyTfliteClassifier.ID -> LegacyTfliteClassifier(context)
		LlmClassifier.ID -> LlmClassifier(context).takeIf { it.isReady() }
		else -> null
	}

	fun close() {
		cache.values.forEach { runCatching { it.close() } }
		cache.clear()
	}
}
