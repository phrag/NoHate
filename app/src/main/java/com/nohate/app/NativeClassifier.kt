package com.nohate.app

object NativeClassifier {
	init {
		System.loadLibrary("nohcore")
	}

	external fun classify(text: String): Float
	external fun classifyWithUserLexicon(text: String, userHate: Array<String>, userSafe: Array<String>): Float

	fun classifyWithUser(text: String, userHate: List<String>, userSafe: List<String>): Float {
		return classifyWithUserLexicon(text, userHate.toTypedArray(), userSafe.toTypedArray())
	}
}
