package com.nohate.app.platform

import android.content.Context

interface CommentProvider {
	fun isConnected(): Boolean
	fun connect(context: Context)
	fun disconnect()
	fun fetchRecentComments(): List<String>
}

class InstagramGraphProvider(private val context: Context) : CommentProvider {
	override fun isConnected(): Boolean = false
	override fun connect(context: Context) { /* TODO: launch Custom Tab OAuth */ }
	override fun disconnect() { /* TODO: clear tokens */ }
	override fun fetchRecentComments(): List<String> = emptyList()
}

class InstagramSessionProvider(private val context: Context) : CommentProvider {
	override fun isConnected(): Boolean = false
	override fun connect(context: Context) { /* TODO: start SessionLoginActivity */ }
	override fun disconnect() { /* TODO: clear cookies */ }
	override fun fetchRecentComments(): List<String> = emptyList()
}
