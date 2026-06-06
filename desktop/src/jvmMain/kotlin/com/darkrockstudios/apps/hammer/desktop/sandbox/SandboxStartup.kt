package com.darkrockstudios.apps.hammer.desktop.sandbox

import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.IS_APP_STORE
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.koin.java.KoinJavaComponent.getKoin
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JOptionPane
import kotlin.system.exitProcess

/**
 * Resolves (or first-time-picks) the user's projects directory under macOS
 * sandboxing before any code touches the filesystem. Must run after Koin
 * starts but before [com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore]
 * (or anything that depends on it) is instantiated — that repo's init reads
 * from the projects dir, which is inaccessible in the sandbox until we
 * activate a security-scoped bookmark for it.
 *
 * No-op on non-sandboxed builds.
 */
object SandboxStartup {

	private val strRes: StrRes by lazy { getKoin().get(StrRes::class) }

	// These dialogs run on the startup thread before any Compose UI exists, so
	// resolve the (suspend) localized strings synchronously.
	private fun str(res: StringResource): String = runBlocking { strRes.get(res) }

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
				val bookmark = MacOsBookmarks.createBookmark(picked.absolutePath)
				if (bookmark != null) {
					val resolved = MacOsBookmarks.resolveAndStartAccess(bookmark)
					if (resolved != null) return resolved.path to bookmark
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

	private fun showNativeDirectoryPicker(): File? {
		// macOS-specific AWT toggle to make FileDialog pick directories rather
		// than files. Restore the previous value so we don't leak this into any
		// other dialog Compose Desktop might open later.
		val key = "apple.awt.fileDialogForDirectories"
		val prior = System.getProperty(key)
		System.setProperty(key, "true")
		try {
			// Don't set dialog.directory — user.home is the sandbox container.
			// NSOpenPanel runs out-of-process and defaults to the real ~/Documents.
			val dialog = FileDialog(null as Frame?, str(Res.string.sandbox_picker_title), FileDialog.LOAD)
			dialog.isVisible = true
			val name = dialog.file ?: return null
			val dir = dialog.directory ?: return null
			return File(dir, name)
		} finally {
			if (prior == null) System.clearProperty(key) else System.setProperty(key, prior)
		}
	}

	// Shown once before the native picker so first-time users understand why
	// macOS is about to ask them to choose a folder.
	private fun confirmIntroOrQuit(): Boolean {
		val options = arrayOf<Any>(str(Res.string.sandbox_choose_folder_button), str(Res.string.sandbox_quit_button))
		val result = JOptionPane.showOptionDialog(
			null,
			str(Res.string.sandbox_intro_message),
			str(Res.string.sandbox_intro_title),
			JOptionPane.YES_NO_OPTION,
			JOptionPane.INFORMATION_MESSAGE,
			null,
			options,
			options[0],
		)
		return result == JOptionPane.YES_OPTION
	}

	private fun confirmRetryOrQuit(): Boolean {
		val options = arrayOf<Any>(str(Res.string.sandbox_choose_folder_button), str(Res.string.sandbox_quit_button))
		val result = JOptionPane.showOptionDialog(
			null,
			str(Res.string.sandbox_retry_message),
			"Hammer",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE,
			null,
			options,
			options[0],
		)
		return result == JOptionPane.YES_OPTION
	}
}
