package com.darkrockstudios.apps.hammer.common.util

import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.hostOs
import java.awt.Desktop
import java.net.URI

class UrlLauncherDesktop : UrlLauncher {
	override fun openInBrowser(url: String) {
		val desktop = Desktop.getDesktop()
		when {
			Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.BROWSE) ->
				desktop.browse(URI.create(url))

			hostOs == HostOs.MacOs -> Runtime.getRuntime().exec("open $url")
			hostOs == HostOs.Linux -> Runtime.getRuntime().exec("xdg-open $url")
			else -> throw UnsupportedOperationException("cannot open $url")
		}
	}
}