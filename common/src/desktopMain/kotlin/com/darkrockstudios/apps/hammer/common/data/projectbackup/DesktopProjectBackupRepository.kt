package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import okio.FileSystem
import java.io.IOException

class DesktopProjectBackupRepository(
	fileSystem: FileSystem,
	projectsRepository: ProjectsRepository,
	clock: Clock
) : ProjectBackupRepository(fileSystem, projectsRepository, clock) {
	override fun supportsBackup() = true

	override suspend fun createBackup(projectDef: ProjectDef): ProjectBackupDef? {
		val projectDir = projectsRepository.getProjectDirectory(projectDef.name)

		val newBackupDef = createNewProjectBackupDef(projectDef)

		return try {
			ZipDirectory.zipDirectory(
				directoryPath = projectDir.path,
				zipFilePath = newBackupDef.path.path
			)

			cullBackups(projectDef)

			newBackupDef
		} catch (e: IOException) {
			Napier.e("Failed to make backup for project: ${projectDef.name}", e)
			null
		}
	}
}