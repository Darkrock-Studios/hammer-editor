package com.darkrockstudios.apps.hammer.common.compose.plugin

/** How to run `hammer` from a terminal or another program, from outside any package sandbox. */
fun cliLauncher(): List<String> {
	val flatpak = System.getenv("FLATPAK_ID")
	val snap = System.getenv("SNAP_NAME")
	return when {
		flatpak != null -> listOf("flatpak", "run", flatpak)
		snap != null -> listOf(snap)
		// Set by jpackage in packaged builds; a development run has no launcher to point at.
		else -> listOf(System.getProperty("jpackage.app-path") ?: "hammer")
	}
}
