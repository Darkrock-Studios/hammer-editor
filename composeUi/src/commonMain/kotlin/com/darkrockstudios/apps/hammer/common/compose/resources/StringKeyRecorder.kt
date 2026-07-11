package com.darkrockstudios.apps.hammer.common.compose.resources

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Records which string resource key produced which resolved text during a
 * composition. Non-null only under previews/tests (see [LocalStringKeyRecorder]);
 * production compositions leave the local null so `.get()` records nothing.
 *
 * Used to build Crowdin screenshot tags: the recorder gives `key -> text` for the
 * strings actually on a screen, which is then joined against the rendered text
 * nodes' bounds.
 */
class StringKeyRecorder {
	private val lock = Any()
	private val entries = LinkedHashSet<Pair<String, String>>()

	fun record(key: String, text: String) {
		synchronized(lock) { entries.add(key to text) }
	}

	fun snapshot(): List<Pair<String, String>> = synchronized(lock) { entries.toList() }
}

val LocalStringKeyRecorder = staticCompositionLocalOf<StringKeyRecorder?> { null }
