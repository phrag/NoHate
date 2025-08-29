package com.nohate.app.platform

import android.content.Context

class InstagramProvider(private val context: Context) {
	fun fetchRecentComments(): List<String> {
		// Stub: replace with on-device session-based fetch
		return listOf(
			"Great post!",
			"This is awful",
			"I disagree",
			"Please be kind"
		)
	}
}
