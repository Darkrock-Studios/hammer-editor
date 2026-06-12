package com.darkrockstudios.apps.hammer.common.util

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

class UrlLauncherDarwin : UrlLauncher {
	override fun openInBrowser(url: String) {
		val nsUrl = NSURL.URLWithString(url) ?: return
		UIApplication.sharedApplication.openURL(
			url = nsUrl,
			options = emptyMap<Any?, Any>(),
			completionHandler = null,
		)
	}
}
