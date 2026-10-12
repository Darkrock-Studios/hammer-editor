package com.darkrockstudios.apps.hammer.common.data.migrator

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import okio.FileSystem

/**
 * Gives the project a client-owned [ProjectId], seeded from the id the server used to assign
 * (`serverProjectId`) so an already-synced project keeps its server identity, and renames the
 * project settings file from `project_data.toml` to `project_settings.toml`.
 */
class Migration3_4(
	private val fileSystem: FileSystem,
	private val projectMetadataDatasource: ProjectMetadataDatasource,
) : Migration {
	override val toVersion: Int = 4

	override fun migrate(projectDef: ProjectDef) {
		Napier.i("Begin Migration3_4 for '${projectDef.name}'...")

		seedProjectId(projectDef)
		renameProjectSettings(projectDef)

		Napier.i("Migration3_4 for '${projectDef.name}' complete.")
	}

	// loadMetadata reads a legacy serverProjectId as the projectId; the save writes it as projectId
	// and drops serverProjectId from the file.
	private fun seedProjectId(projectDef: ProjectDef) {
		projectMetadataDatasource.updateMetadata(projectDef) {
			if (it.info.projectId != null) it
			else it.copy(info = it.info.copy(projectId = ProjectId.randomUUID()))
		}
	}

	private fun renameProjectSettings(projectDef: ProjectDef) {
		val projectDir = projectDef.path.toOkioPath()
		val legacy = projectDir / LEGACY_PROJECT_SETTINGS_FILENAME
		val current = projectDir / ProjectDataDatasource.FILENAME

		if (!fileSystem.exists(legacy)) return

		if (fileSystem.exists(current)) {
			// Never pick a winner between two copies of user data; both stay for the user to reconcile.
			Napier.w(
				"Migration3_4: both $LEGACY_PROJECT_SETTINGS_FILENAME and " +
					"${ProjectDataDatasource.FILENAME} exist in '${projectDef.name}', leaving both"
			)
			return
		}

		fileSystem.atomicMove(legacy, current)
	}

	companion object {
		const val LEGACY_PROJECT_SETTINGS_FILENAME = "project_data.toml"
	}
}
