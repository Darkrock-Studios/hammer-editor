package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.util.zip.unzipToDirectory
import com.darkrockstudios.apps.hammer.common.util.zip.zipDirectory
import io.github.aakira.napier.Napier
import okio.FileSystem
import kotlin.time.Clock

actual fun createProjectBackup(
	fileSystem: FileSystem,
	projectsRepository: ProjectsRepository,
	clock: Clock
): ProjectBackupRepository {
	return IosProjectBackupRepository(fileSystem, projectsRepository, clock)
}

class IosProjectBackupRepository(
	fileSystem: FileSystem,
	projectsRepository: ProjectsRepository,
	clock: Clock
) : ProjectBackupRepository(fileSystem, projectsRepository, clock) {
	override fun supportsBackup() = true

	override suspend fun createBackup(projectDef: ProjectDef): ProjectBackupDef? {
		val projectDir = projectsRepository.getProjectDirectory(projectDef.name).toOkioPath()
		val newBackupDef = createNewProjectBackupDef(projectDef)

		return try {
			zipDirectory(
				fileSystem = fileSystem,
				sourceDirectory = projectDir,
				destinationZip = newBackupDef.path.toOkioPath(),
				skipHiddenFiles = true
			)

			cullBackups(projectDef)

			newBackupDef
		} catch (e: Exception) {
			Napier.e("Failed to make backup for project: ${projectDef.name}", e)
			null
		}
	}

	override suspend fun restoreBackup(backupDef: ProjectBackupDef, targetDir: HPath): Boolean {
		return try {
			unzipToDirectory(
				fileSystem = fileSystem,
				zipPath = backupDef.path.toOkioPath(),
				destinationDirectory = targetDir.toOkioPath()
			)
			true
		} catch (e: Exception) {
			Napier.e("Failed to restore backup: ${backupDef.path.name}", e)
			false
		}
	}
}