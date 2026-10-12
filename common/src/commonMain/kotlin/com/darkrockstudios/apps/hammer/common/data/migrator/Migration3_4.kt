package com.darkrockstudios.apps.hammer.common.data.migrator

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataDatasource
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import okio.FileSystem

/**
 * Renames the project settings file from `project_data.toml` to `project_settings.toml`.
 */
class Migration3_4(
	private val fileSystem: FileSystem,
) : Migration {
	override val toVersion: Int = 4

	override fun migrate(projectDef: ProjectDef) {
		Napier.i("Begin Migration3_4 for '${projectDef.name}'...")

		renameProjectSettings(projectDef)

		Napier.i("Migration3_4 for '${projectDef.name}' complete.")
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
