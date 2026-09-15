package com.darkrockstudios.apps.hammer.common

actual fun platformStartupInfo(): String =
	desktopStartupInfo(hostOs, System::getProperty, System::getenv)

internal fun desktopStartupInfo(
	os: HostOs,
	property: (String) -> String?,
	environment: (String) -> String?,
): String {
	fun env(name: String) = environment(name) ?: "n/a"
	fun prop(name: String) = property(name) ?: "n/a"
	return "OS: ${prop("os.name")} ${prop("os.version")} (${prop("os.arch")})" +
		" | JVM: ${prop("java.vendor")} ${prop("java.runtime.version")}" +
		// Display server + desktop environment: prime suspects for Compose/Skia startup stalls on Linux.
		(if (os == HostOs.Linux) " | session: ${env("XDG_SESSION_TYPE")}/${env("XDG_CURRENT_DESKTOP")}" else "") +
		" | renderApi: ${property("skiko.renderApi") ?: "default"}"
}
