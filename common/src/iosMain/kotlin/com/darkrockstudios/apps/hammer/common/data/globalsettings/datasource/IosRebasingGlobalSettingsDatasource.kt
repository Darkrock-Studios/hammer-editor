package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore.Companion.DEFAULT_PROJECTS_DIR
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory

/**
 * Repairs stale [GlobalSettings.projectsDirectory] paths on load. iOS sandbox
 * containers (`Containers/Data/Application/<UUID>/Documents/...`) can be re-UUID'd
 * between installs, dangling any absolute path a prior install persisted. We
 * re-anchor the post-`/Documents/` suffix under the live Documents directory.
 *
 * Pure read: the rebased value is returned in memory only, never written here.
 * loadSettings runs during store construction, and a write at that point re-enters
 * the still-constructing store and loops forever (the v3.5.0 bootstrap cycle fixed
 * for the common load paths in 3.5.1). The correction persists on the next store.
 */
internal class IosRebasingGlobalSettingsDatasource(
	private val delegate: GlobalSettingsFilesystemDatasource,
) : GlobalSettingsDatasource by delegate {

	override fun loadSettings(): GlobalSettings {
		val loaded = delegate.loadSettings()
		val rebased = rebaseProjectsDirectory(loaded.projectsDirectory, getDefaultRootDocumentDirectory())
		return if (rebased == loaded.projectsDirectory) {
			loaded
		} else {
			loaded.copy(projectsDirectory = rebased)
		}
	}
}

/** Re-anchors a stored projects-directory path under the live Documents directory. */
internal fun rebaseProjectsDirectory(stored: String, currentDocuments: String): String {
	if (stored == currentDocuments || stored.startsWith("$currentDocuments/")) {
		return stored
	}

	val marker = "/Documents/"
	val idx = stored.indexOf(marker)
	val suffix = if (idx >= 0) stored.substring(idx + marker.length) else DEFAULT_PROJECTS_DIR
	return "$currentDocuments/$suffix"
}
