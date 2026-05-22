package com.nohate.app.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object InstagramIntents {
	private const val INSTAGRAM_PACKAGE = "com.instagram.android"

	fun openPost(context: Context, sourceUrl: String) {
		val uri = Uri.parse(sourceUrl)
		val appIntent = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(INSTAGRAM_PACKAGE) }
		val pm: PackageManager = context.packageManager
		if (appIntent.resolveActivity(pm) != null) {
			context.startActivity(appIntent)
		} else {
			context.startActivity(Intent(Intent.ACTION_VIEW, uri))
		}
	}

	fun openReportHelp(context: Context) {
		context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://help.instagram.com/165828726894770")))
	}

	fun openUserProfile(context: Context, handle: String) {
		val clean = handle.trimStart('@')
		val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$clean/")).apply {
			setPackage(INSTAGRAM_PACKAGE)
		}
		if (appIntent.resolveActivity(context.packageManager) != null) {
			context.startActivity(appIntent)
		} else {
			context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$clean/")))
		}
	}
}
