package com.darkrockstudios.apps.hammer.desktop.sandbox

import com.russhwolf.settings.Settings

/**
 * Persists the base64-encoded macOS security-scoped bookmark for the user's
 * projects directory. Sandbox machinery, not a user preference — lives in
 * [Settings] (platform-native key/value store) alongside other desktop-only
 * persisted state like [com.darkrockstudios.apps.hammer.desktop.WindowGeometryStore],
 * rather than alongside the TOML-serialized user prefs in GlobalSettings.
 */
class SandboxBookmarkStore(private val settings: Settings) {
	fun loadProjectsDirBookmark(): String? = settings.getStringOrNull(KEY)

	fun saveProjectsDirBookmark(bookmark: String) {
		settings.putString(KEY, bookmark)
	}

	fun clearProjectsDirBookmark() {
		settings.remove(KEY)
	}

	companion object {
		private const val KEY = "desktop.sandbox.projectsDirBookmark"
	}
}
