package com.nohate.app.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.nohate.app.data.SecureStore

class SessionLoginActivity : Activity() {
	private lateinit var webView: WebView

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		webView = WebView(this)
		setContentView(webView, ViewGroup.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.MATCH_PARENT
		))

		if (WebViewFeature.isFeatureSupported(WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL)) {
			// Using a modern WebView; ensure isolation policies later if needed
		}

		CookieManager.getInstance().setAcceptCookie(true)
		CookieManager.getInstance().removeAllCookies(null)
		CookieManager.getInstance().flush()

		val settings: WebSettings = webView.settings
		settings.javaScriptEnabled = true
		settings.domStorageEnabled = true
		settings.userAgentString = settings.userAgentString + " NoHate/1.0"

		webView.webViewClient = object : WebViewClient() {
			override fun onPageFinished(view: WebView?, url: String?) {
				super.onPageFinished(view, url)
				// If we detect we are logged in, capture session cookies for instagram.com and finish
				if (url != null && url.contains("instagram.com")) {
					val cookies = CookieManager.getInstance().getCookie(url)
					if (cookies != null && cookies.contains("sessionid")) {
						val store = SecureStore(this@SessionLoginActivity)
						store.setSessionCookies("instagram", cookies)
						finish()
					}
				}
			}
		}

		webView.loadUrl("https://www.instagram.com/accounts/login/")
	}

	override fun onDestroy() {
		try {
			(webView.parent as? ViewGroup)?.removeView(webView)
			webView.destroy()
		} catch (_: Throwable) {}
		super.onDestroy()
	}
}
