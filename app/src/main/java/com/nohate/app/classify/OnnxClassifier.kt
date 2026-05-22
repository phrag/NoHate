package com.nohate.app.classify

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.LongBuffer
import kotlin.math.exp

/**
 * ONNX Runtime Mobile backend for text classification.
 *
 * Supports two model shapes:
 *  - `tokenizerEmbedded = true`: graph contains a BertTokenizer / SentencePiece
 *    custom op (ONNX Runtime Extensions). One string-tensor input.
 *  - `tokenizerEmbedded = false`: plain transformer with two long-tensor inputs
 *    (`input_ids`, `attention_mask`). We tokenize on the Kotlin side via
 *    [BertTokenizer] using a bundled `vocab.txt` from `entry.tokenizerAsset`.
 *
 * Output is interpreted according to [ModelEntry.OutputKind]:
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
    private val singleInputName: String
    private val tokenizer: BertTokenizer?
    private val idsInputName: String
    private val maskInputName: String

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
        var idsName = "input_ids"
        var maskName = "attention_mask"
        var tok: BertTokenizer? = null
        try {
            val bytes = loadModelBytes()
            if (bytes != null) {
                env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
                    runCatching { registerCustomOpLibraryReflective(this) }
                }
                val s = env!!.createSession(bytes, opts)
                session = s
                val inputs = s.inputNames.toList()
                resolvedInput = inputs.firstOrNull() ?: "text"
                idsName = inputs.firstOrNull { it.contains("input_ids", ignoreCase = true) } ?: inputs.firstOrNull() ?: "input_ids"
                maskName = inputs.firstOrNull { it.contains("attention_mask", ignoreCase = true) } ?: inputs.getOrNull(1) ?: "attention_mask"

                if (!entry.tokenizerEmbedded) {
                    val vocabPath = entry.tokenizerAsset
                    if (vocabPath != null) {
                        try {
                            val vocab = BertTokenizer.loadVocab(context, vocabPath)
                            tok = BertTokenizer(vocab)
                            Log.d(TAG, "${entry.id}: loaded vocab size=${vocab.size} from $vocabPath; ids=$idsName mask=$maskName")
                        } catch (t: Throwable) {
                            Log.e(TAG, "${entry.id}: vocab load failed for $vocabPath", t)
                            session = null // refuse to advertise readiness without tokenizer
                        }
                    } else {
                        Log.e(TAG, "${entry.id}: tokenizerEmbedded=false but no `tokenizer` asset in registry")
                        session = null
                    }
                }

                if (session != null) runCatching { classifyInternal("warmup") }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "init failed for ${entry.id}", t)
            session = null
        }
        singleInputName = resolvedInput
        idsInputName = idsName
        maskInputName = maskName
        tokenizer = tok
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
    }

    private fun classifyInternal(text: String): Float {
        val s = session ?: return 0f
        val e = env ?: return 0f
        return try {
            val tok = tokenizer
            if (tok != null) {
                val enc = tok.encode(text)
                val shape = longArrayOf(1L, enc.inputIds.size.toLong())
                val idsTensor = OnnxTensor.createTensor(e, LongBuffer.wrap(enc.inputIds), shape)
                val maskTensor = OnnxTensor.createTensor(e, LongBuffer.wrap(enc.attentionMask), shape)
                idsTensor.use {
                    maskTensor.use {
                        s.run(mapOf(idsInputName to idsTensor, maskInputName to maskTensor)).use { result ->
                            interpret(result[0].value)
                        }
                    }
                }
            } else {
                val tensor = OnnxTensor.createTensor(e, arrayOf(text))
                tensor.use {
                    s.run(mapOf(singleInputName to it)).use { result ->
                        interpret(result[0].value)
                    }
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
                else {
                    val hateIdx = entry.hateLabelIndex.coerceIn(0, 1)
                    val notHateIdx = 1 - hateIdx
                    softmaxHate(flat[notHateIdx], flat[hateIdx])
                }
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
