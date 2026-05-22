package com.nohate.app.classify

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.exp

/**
 * ONNX Runtime Mobile backend for text classification.
 *
 * Phase 2 assumes the bundled .onnx file has its tokenizer baked in via
 * ONNX Runtime Extensions (BertTokenizer / SentencePieceTokenizer custom op).
 * The model therefore takes a single string tensor as input. If we later need
 * a separate tokenizer (Rust JNI path — see ADR-004), an alternate classifier
 * will be added rather than complicating this one.
 *
 * Output is interpreted according to [ModelEntry.outputKind]:
 *   - SIGMOID_SINGLE: one logit  -> sigmoid -> probability.
 *   - SOFTMAX_PAIR:   two logits -> softmax -> P(hate).
 *   - RAW_LOGIT:      already in [0,1].
 */
class OnnxClassifier(
	private val context: Context,
	private val entry: ModelEntry,
) : Classifier {
	private var session: OrtSession? = null
	private var env: OrtEnvironment? = null
	private val inputName: String

	override val info: ClassifierInfo = ClassifierInfo(
		id = entry.id,
		displayName = entry.displayName,
		version = entry.version,
		sizeBytes = entry.sizeBytes,
		estPeakMemMb = entry.estPeakMemMb,
		license = entry.license,
		languages = entry.languages,
		source = if (entry.bundled) ClassifierInfo.Source.BUNDLED else ClassifierInfo.Source.DOWNLOADED,
	)

	init {
		var resolvedInput = "text"
		try {
			val bytes = loadModelBytes()
			if (bytes != null) {
				env = OrtEnvironment.getEnvironment()
				val opts = OrtSession.SessionOptions().apply {
					setIntraOpNumThreads(2)
					setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
					// Register ORT Extensions so the embedded tokenizer custom op resolves.
					runCatching { registerCustomOpLibraryReflective(this) }
				}
				val s = env!!.createSession(bytes, opts)
				session = s
				resolvedInput = s.inputNames.firstOrNull() ?: "text"
				runCatching { classifyInternal("warmup") }
			}
		} catch (t: Throwable) {
			Log.e(TAG, "init failed for ${entry.id}", t)
			session = null
		}
		inputName = resolvedInput
	}

	override fun isReady(): Boolean = session != null

	override fun classify(text: String): Score {
		val t0 = System.nanoTime()
		val p = classifyInternal(text)
		val dt = System.nanoTime() - t0
		val label = if (p >= 0.5f) "hate" else "not_hate"
		return Score(probability = p, label = label, latencyNanos = dt, backend = entry.id)
	}

	override fun close() {
		runCatching { session?.close() }
		session = null
		// OrtEnvironment is a process-wide singleton; do not close.
	}

	private fun classifyInternal(text: String): Float {
		val s = session ?: return 0f
		val e = env ?: return 0f
		return try {
			val tensor = OnnxTensor.createTensor(e, arrayOf(text))
			tensor.use {
				s.run(mapOf(inputName to it)).use { result ->
					val raw = result[0].value
					interpret(raw)
				}
			}
		} catch (t: Throwable) {
			Log.w(TAG, "classify failed for ${entry.id}: ${t.message}")
			0f
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun interpret(raw: Any?): Float {
		val flat: FloatArray = when (raw) {
			is FloatArray -> raw
			is Array<*> -> flatten(raw)
			else -> return 0f
		}
		return when (entry.outputKind) {
			ModelEntry.OutputKind.SIGMOID_SINGLE -> sigmoid(flat.firstOrNull() ?: 0f)
			ModelEntry.OutputKind.RAW_LOGIT -> (flat.firstOrNull() ?: 0f).coerceIn(0f, 1f)
			ModelEntry.OutputKind.SOFTMAX_PAIR -> {
				if (flat.size < 2) sigmoid(flat.firstOrNull() ?: 0f)
				else softmaxHate(flat[0], flat[1])
			}
		}
	}

	private fun flatten(a: Array<*>): FloatArray {
		val out = ArrayList<Float>()
		fun walk(x: Any?) {
			when (x) {
				is FloatArray -> x.forEach { out.add(it) }
				is Array<*> -> x.forEach { walk(it) }
				is Float -> out.add(x)
				is Double -> out.add(x.toFloat())
			}
		}
		walk(a)
		return FloatArray(out.size) { out[it] }
	}

	private fun sigmoid(z: Float): Float = (1f / (1f + exp(-z.toDouble()))).toFloat().coerceIn(0f, 1f)

	private fun softmaxHate(notHate: Float, hate: Float): Float {
		val m = maxOf(notHate, hate)
		val a = exp((notHate - m).toDouble())
		val b = exp((hate - m).toDouble())
		val denom = a + b
		return if (denom == 0.0) 0f else (b / denom).toFloat().coerceIn(0f, 1f)
	}

	private fun loadModelBytes(): ByteArray? {
		entry.asset?.let { name ->
			return try {
				context.assets.open(name).use { ins ->
					val bos = ByteArrayOutputStream(entry.sizeBytes.toInt().coerceAtLeast(8 * 1024))
					ins.copyTo(bos)
					bos.toByteArray()
				}
			} catch (t: Throwable) {
				Log.w(TAG, "asset read failed for ${entry.id}: ${t.message}")
				null
			}
		}
		val downloaded: File = ModelDownloader.localFile(context, entry)
		if (downloaded.exists()) return runCatching { downloaded.readBytes() }.getOrNull()
		return null
	}

	/**
	 * Reflectively register ORT Extensions if the dependency is present on the
	 * classpath. Keeping the call reflective avoids a hard compile-time edge
	 * on the Extensions package, which has moved a few times between releases.
	 */
	private fun registerCustomOpLibraryReflective(opts: OrtSession.SessionOptions) {
		val cls = runCatching { Class.forName("ai.onnxruntime.extensions.OrtxPackage") }.getOrNull()
			?: runCatching { Class.forName("com.microsoft.onnxruntime.extensions.OrtxPackage") }.getOrNull()
			?: return
		val getter = cls.methods.firstOrNull { it.name == "getLibraryPath" && it.parameterCount == 0 }
			?: return
		val path = getter.invoke(null) as? String ?: return
		opts.registerCustomOpLibrary(path)
	}

	companion object {
		private const val TAG = "OnnxClassifier"
	}
}
