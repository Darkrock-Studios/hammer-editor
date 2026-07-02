package com.darkrockstudios.apps.hammer.common

actual fun platformStartupInfo(): String {
	fun env(name: String) = System.getenv(name) ?: "n/a"
	fun prop(name: String) = System.getProperty(name) ?: "n/a"
	return "OS: ${prop("os.name")} ${prop("os.version")} (${prop("os.arch")})" +
		" | JVM: ${prop("java.vendor")} ${prop("java.runtime.version")}" +
		// Display server + desktop environment: prime suspects for Compose/Skia startup stalls on Linux.
		" | session: ${env("XDG_SESSION_TYPE")}/${env("XDG_CURRENT_DESKTOP")}" +
		" | renderApi: ${System.getProperty("skiko.renderApi", "default")}"
}
