package com.darkrockstudios.apps.hammer.common.util

import java.awt.Desktop
import java.net.URI
import java.util.Locale

class UrlLauncherDesktop : UrlLauncher {
	override fun openInBrowser(url: String) {
		val osName by lazy(LazyThreadSafetyMode.NONE) {
			System.getProperty("os.name").lowercase(Locale.getDefault())
		}
		val desktop = Desktop.getDesktop()
		when {
			Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE) ->
				desktop.browse(URI.create(url))

			"mac" in osName -> Runtime.getRuntime().exec("open $url")
			"nix" in osName || "nux" in osName -> Runtime.getRuntime().exec("xdg-open $url")
			else -> throw RuntimeException("cannot open $url")
		}
	}
}