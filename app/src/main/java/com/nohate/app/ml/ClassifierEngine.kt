package com.nohate.app.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.InputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

interface ClassifierEngine {
	fun classify(text: String): Float
}

class RulesEngine(private val context: Context) : ClassifierEngine {
	override fun classify(text: String): Float = 0f
}

class TfliteClassifier(private val context: Context) : ClassifierEngine {
	private val interpreter: Interpreter? by lazy { loadInterpreter() }

	override fun classify(text: String): Float {
		val tfl = interpreter ?: return 0f
		return try {
			val input = preprocess(text)
			val output = Array(1) { FloatArray(1) }
			tfl.run(input, output)
			val score = output[0][0]
			if (score.isNaN()) 0f else score.coerceIn(0f, 1f)
		} catch (_: Throwable) {
			0f
		}
	}

	private fun preprocess(text: String): Array<FloatArray> {
		val len = text.length.coerceAtMost(512)
		val v = len / 512f
		return arrayOf(floatArrayOf(v))
	}

	private fun loadInterpreter(): Interpreter? {
		return try {
			// Verify checksum if available
			val expected = readAssetText("model.sha256")?.trim()
			val modelBuffer = loadModelFromAssets("model.tflite") ?: return null
			if (!expected.isNullOrEmpty()) {
				val actual = sha256Asset("model.tflite")
				if (actual == null || !actual.equals(expected, ignoreCase = true)) {
					return null
				}
			}
			val opts = Interpreter.Options().apply { setNumThreads(2) }
			val interp = Interpreter(modelBuffer, opts)
			// Warmup best-effort
			try { classify("warmup") } catch (_: Throwable) { /* ignore */ }
			interp
		} catch (_: Throwable) {
			null
		}
	}

	private fun readAssetText(name: String): String? = try {
		context.assets.open(name).use { it.reader().readText() }
	} catch (_: Throwable) { null }

	private fun sha256Asset(name: String): String? = try {
		context.assets.open(name).use { ins -> sha256Hex(ins) }
	} catch (_: Throwable) { null }

	private fun sha256Hex(ins: InputStream): String {
		val md = MessageDigest.getInstance("SHA-256")
		val buf = ByteArray(8192)
		while (true) {
			val r = ins.read(buf)
			if (r <= 0) break
			md.update(buf, 0, r)
		}
		return md.digest().joinToString("") { b -> "%02x".format(b) }
	}

	private fun loadModelFromAssets(name: String): MappedByteBuffer? {
		return try {
			context.assets.openFd(name).use { afd ->
				FileChannel.MapMode.READ_ONLY.let { mode ->
					afd.createInputStream().channel.use { channel ->
						channel.map(mode, afd.startOffset, afd.declaredLength)
					}
				}
			}
		} catch (_: Throwable) {
			null
		}
	}
}
