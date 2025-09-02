package com.nohate.app.llm

object LlamaBridge {
	init {
		try { System.loadLibrary("llamabridge") } catch (_: Throwable) {}
	}

	external fun loadModel(path: String): Boolean
	external fun classify(text: String, prompt: String): String
}

