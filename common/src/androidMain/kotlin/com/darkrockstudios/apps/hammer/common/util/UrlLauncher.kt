package com.darkrockstudios.apps.hammer.common.util

import android.content.Context
import android.content.Intent
import android.net.Uri


class UrlLauncherAndroid(private val context: Context) : UrlLauncher {
	override fun openInBrowser(url: String) {
		val intent = Intent(Intent.ACTION_VIEW)
		intent.data = Uri.parse(url)
		context.startActivity(intent)
	}
}