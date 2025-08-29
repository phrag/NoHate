package com.nohate.app.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import com.nohate.app.data.SecureStore

class OAuthCallbackActivity : Activity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val data: Uri? = intent?.data
		if (data != null && data.scheme == "nohate" && data.host == "oauth") {
			// TODO: parse provider, code, state and perform token exchange for providers that allow PKCE without app secret.
			// For now, just echo back and finish.
			val store = SecureStore(this)
			// store.setOAuthToken(provider, token) // Placeholder
		}
		finish()
	}

	companion object {
		fun buildCallbackUri(provider: String): Uri = "nohate://oauth/${provider}/callback".toUri()
	}
}
