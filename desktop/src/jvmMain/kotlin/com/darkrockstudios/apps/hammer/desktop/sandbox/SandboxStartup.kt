package com.darkrockstudios.apps.hammer.desktop.sandbox

import com.darkrockstudios.apps.hammer.common.IS_APP_STORE
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import io.github.aakira.napier.Napier
import org.koin.java.KoinJavaComponent.getKoin
import kotlin.system.exitProcess

/**
 * Resolves (or first-time-picks) the user's projects directory under macOS
 * sandboxing before any code touches the filesystem. Must run after Koin
 * starts but before [com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore]
 * (or anything that depends on it) is instantiated — that repo's init reads
 * from the projects dir, which is inaccessible in the sandbox until we
 * activate a security-scoped bookmark for it.
 *
 * Nothing on this path may touch AWT, directly or indirectly. All of it runs
 * before the Compose application starts, and whatever creates `NSApp` first
 * owns it: if that is AWT, the Tao backend's event loop — which blocks the
 * main thread expecting AppKit events — never gets any, and the app runs on
 * with no window and no crash. Two separate things used to do exactly that,
 * and both had to go: the Swing/AWT dialogs (now native AppKit via
 * [MacOsBookmarks]) and Compose Resources string lookup (now [SandboxStrings],
 * because `getString()` reaches `Toolkit.getDefaultToolkit()` on desktop).
 *
 * The hang only ever bites on first launch — later launches resolve a stored
 * bookmark and never get here — which is exactly how it reached App Review.
 *
 * No-op on non-sandboxed builds.
 */
object SandboxStartup {

	private fun str(key: String): String = SandboxStrings.get(key)

	fun ensureProjectsDirAccess() {
		if (!IS_APP_STORE) return
		if (!MacOsBookmarks.isAvailable) {
			Napier.w("App Store flavor but bookmark dylib unavailable — projects-dir access likely to fail")
			return
		}

		val bookmarkStore: SandboxBookmarkStore = getKoin().get(SandboxBookmarkStore::class)
		val datasource: GlobalSettingsDatasource =
			getKoin().get(GlobalSettingsDatasource::class)

		// Try the stored bookmark first.
		bookmarkStore.loadProjectsDirBookmark()?.let { stored ->
			val resolved = MacOsBookmarks.resolveAndStartAccess(stored)
			if (resolved != null) {
				refreshIfNeeded(bookmarkStore, datasource, resolved)
				return
			}
			Napier.w("Stored projects-dir bookmark failed to resolve — prompting user to pick again")
			bookmarkStore.clearProjectsDirBookmark()
		}

		// First run, or the stored bookmark went bad — pick fresh.
		val (path, bookmark) = pickDirectoryUntilSuccessfulOrQuit()
		bookmarkStore.saveProjectsDirBookmark(bookmark)
		datasource.storeSettings(datasource.loadSettings().copy(projectsDirectory = path))
		Napier.i("Projects directory set to picked path: $path")
	}

	private fun refreshIfNeeded(
		bookmarkStore: SandboxBookmarkStore,
		datasource: GlobalSettingsDatasource,
		resolved: MacOsBookmarks.Resolved,
	) {
		// Stale bookmarks should be regenerated while we still hold access.
		if (resolved.isStale) {
			MacOsBookmarks.createBookmark(resolved.path)?.let(bookmarkStore::saveProjectsDirBookmark)
		}
		// Path may have moved out from under us; sync the user-visible setting.
		val settings = datasource.loadSettings()
		if (resolved.path != settings.projectsDirectory) {
			datasource.storeSettings(settings.copy(projectsDirectory = resolved.path))
		}
	}

	private fun pickDirectoryUntilSuccessfulOrQuit(): Pair<String, String> {
		if (!confirmIntroOrQuit()) {
			Napier.i("User dismissed the projects-directory intro — exiting")
			exitProcess(0)
		}
		while (true) {
			val picked = showNativeDirectoryPicker()
			if (picked != null) {
				Napier.i("User picked projects directory: $picked")
				val bookmark = MacOsBookmarks.createBookmark(picked)
				if (bookmark != null) {
					val resolved = MacOsBookmarks.resolveAndStartAccess(bookmark)
					if (resolved != null) {
						Napier.i("Bookmark for picked directory resolved to: ${resolved.path}")
						return resolved.path to bookmark
					}
					Napier.w("Could not start access on a freshly-created bookmark; retrying")
				} else {
					Napier.w("Could not create bookmark for picked directory; retrying")
				}
			}
			if (!confirmRetryOrQuit()) {
				Napier.i("User declined to pick a projects directory — exiting")
				exitProcess(0)
			}
		}
	}

	// Same string for title and message on purpose: modern macOS hides an open
	// panel's title bar, so `message` is the only one the user actually reads.
	private fun showNativeDirectoryPicker(): String? = MacOsBookmarks.pickDirectory(
		title = str(SandboxStrings.PICKER_TITLE),
		message = str(SandboxStrings.PICKER_TITLE),
		prompt = str(SandboxStrings.CHOOSE_FOLDER_BUTTON),
	)

	// Shown once before the native picker so first-time users understand why
	// macOS is about to ask them to choose a folder.
	private fun confirmIntroOrQuit(): Boolean = MacOsBookmarks.confirm(
		title = str(SandboxStrings.INTRO_TITLE),
		message = str(SandboxStrings.INTRO_MESSAGE),
		primaryButton = str(SandboxStrings.CHOOSE_FOLDER_BUTTON),
		secondaryButton = str(SandboxStrings.QUIT_BUTTON),
	)

	private fun confirmRetryOrQuit(): Boolean = MacOsBookmarks.confirm(
		title = "Hammer",
		message = str(SandboxStrings.RETRY_MESSAGE),
		primaryButton = str(SandboxStrings.CHOOSE_FOLDER_BUTTON),
		secondaryButton = str(SandboxStrings.QUIT_BUTTON),
	)
}
