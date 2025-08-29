package com.nohate.app

object NativeClassifier {
	init {
		System.loadLibrary("nohcore")
	}

	external fun classify(text: String): Float
}
