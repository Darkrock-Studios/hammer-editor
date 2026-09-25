package com.darkrockstudios.apps.hammer.common.compose.plugin

import com.darkrockstudios.apps.hammer.base.DistributionChannel
import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.hostOs

/** How to run `hammer` from a terminal or another program, from outside any package sandbox. */
fun cliLauncher(
	env: Map<String, String> = System.getenv(),
	// Set by jpackage in packaged builds; a development run has no launcher to point at.
	appPath: String? = System.getProperty("jpackage.app-path"),
	os: HostOs = hostOs,
	channel: DistributionChannel = DistributionChannel.current,
): List<String> {
	env["FLATPAK_ID"]?.let { return listOf("flatpak", "run", it) }
	env["SNAP_NAME"]?.let { return listOf(it) }
	// The mounted image moves on every run; the AppImage file does not.
	env["APPIMAGE"]?.let { return listOf(it) }
	if (os == HostOs.Windows) {
		// A Store app's install folder cannot be run from directly; its execution alias can.
		if (channel == DistributionChannel.MICROSOFT_STORE) return listOf("hammer")
		appPath?.let { return listOf(windowsConsoleLauncher(it)) }
	}
	return listOf(appPath ?: "hammer")
}

/** The console twin the Windows build ships beside the GUI launcher at [appPath], which shows no output in a terminal. */
fun windowsConsoleLauncher(appPath: String): String =
	appPath.substring(0, appPath.lastIndexOfAny(charArrayOf('\\', '/')) + 1) + "hammer-cli.exe"
