package com.darkrockstudios.apps.hammer.common.util

import java.awt.Desktop
import java.net.URI

class UrlLauncherDesktop : UrlLauncher {
	override fun openInBrowser(url: String) {
		val desktop = Desktop.getDesktop()
		if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE)) {
			desktop.browse(URI.create(url))
		} else {
			throw UnsupportedOperationException("cannot open $url")
		}
	}
}