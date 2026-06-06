package com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore.Companion.DEFAULT_PROJECTS_DIR
import com.darkrockstudios.apps.hammer.common.getDefaultRootDocumentDirectory

/**
 * Repairs stale [GlobalSettings.projectsDirectory] paths on load. iOS sandbox
 * containers (`Containers/Data/Application/<UUID>/Documents/...`) can be
 * re-UUID'd between installs, dangling any absolute path a prior install
 * persisted. We re-anchor the post-`/Documents/` suffix under the live
 * Documents directory and persist the correction.
 */
internal class IosRebasingGlobalSettingsDatasource(
	private val delegate: GlobalSettingsFilesystemDatasource,
) : GlobalSettingsDatasource by delegate {

	override fun loadSettings(): GlobalSettings {
		val loaded = delegate.loadSettings()
		val rebased = rebaseIfStale(loaded.projectsDirectory)
		return if (rebased == loaded.projectsDirectory) {
			loaded
		} else {
			val corrected = loaded.copy(projectsDirectory = rebased)
			delegate.storeSettings(corrected)
			corrected
		}
	}

	private fun rebaseIfStale(stored: String): String {
		val currentDocuments = getDefaultRootDocumentDirectory()
		if (stored == currentDocuments || stored.startsWith("$currentDocuments/")) {
			return stored
		}

		val marker = "/Documents/"
		val idx = stored.indexOf(marker)
		val suffix = if (idx >= 0) stored.substring(idx + marker.length) else DEFAULT_PROJECTS_DIR
		return "$currentDocuments/$suffix"
	}
}
