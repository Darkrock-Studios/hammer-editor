package com.darkrockstudios.apps.hammer.desktop.sandbox

import com.darkrockstudios.apps.hammer.common.sandbox.SandboxFileAccess
import io.github.aakira.napier.Napier

internal class MacOsSandboxFileAccess(private val bookmarkStore: SandboxBookmarkStore) : SandboxFileAccess {
	override val isSandboxed: Boolean = true

	override fun establishAccessForNewDirectory(path: String): Boolean {
		val newBookmark = MacOsBookmarks.createBookmark(path)
		if (newBookmark == null) {
			Napier.w("Sandbox bookmark creation failed for $path")
			return false
		}

		// Capture before resolveAndStartAccess overwrites activePath.
		val previouslyActive = MacOsBookmarks.activePath

		val resolved = MacOsBookmarks.resolveAndStartAccess(newBookmark)
		if (resolved == null) {
			Napier.w("Resolved-and-start failed on freshly-created bookmark for $path")
			return false
		}

		bookmarkStore.saveProjectsDirBookmark(newBookmark)

		// Balance startAccessing/stopAccessing or the per-process scoped-resource budget leaks.
		if (previouslyActive != null && previouslyActive != resolved.path) {
			MacOsBookmarks.stopAccess(previouslyActive)
		}

		return true
	}
}
