package com.darkrockstudios.apps.hammer.desktop.sandbox

import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.common.sandbox.SandboxFileAccess

internal class MacOsSandboxFileAccess(private val bookmarkStore: SandboxBookmarkStore) : SandboxFileAccess {
	override val isSandboxed: Boolean = true

	override fun establishAccessForNewDirectory(path: String): Boolean {
		val newBookmark = MacOsBookmarks.createBookmark(path)
		if (newBookmark == null) {
			Logger.w("Sandbox bookmark creation failed for $path")
			return false
		}

		// Capture before resolveAndStartAccess overwrites activePath.
		val previouslyActive = MacOsBookmarks.activePath

		val resolved = MacOsBookmarks.resolveAndStartAccess(newBookmark)
		if (resolved == null) {
			Logger.w("Resolved-and-start failed on freshly-created bookmark for $path")
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
