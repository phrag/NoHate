package com.nohate.app

import android.util.Log

object NativeClassifier {
	/**
	 * `true` once `libnohcore.so` has been loaded. If the .so is missing from
	 * `jniLibs/<abi>/` (most commonly because `./scripts/build_rust_android.sh`
	 * hasn't been run for this ABI), this stays `false` and call sites should
	 * route around the rules backend rather than throwing on every comment.
	 */
	@JvmStatic
	val isLibraryLoaded: Boolean = try {
		System.loadLibrary("nohcore")
		true
	} catch (t: Throwable) {
		Log.e("NativeClassifier", "failed to load libnohcore.so — run ./scripts/build_rust_android.sh", t)
		false
	}

	external fun classify(text: String): Float
	external fun classifyWithUserLexicon(text: String, userHate: Array<String>, userSafe: Array<String>): Float

	fun classifyWithUser(text: String, userHate: List<String>, userSafe: List<String>): Float {
		if (!isLibraryLoaded) return 0f
		return classifyWithUserLexicon(text, userHate.toTypedArray(), userSafe.toTypedArray())
	}
}
