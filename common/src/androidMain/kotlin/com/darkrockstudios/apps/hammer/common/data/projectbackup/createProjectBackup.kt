package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import kotlinx.datetime.Clock
import okio.FileSystem

actual fun createProjectBackup(
	fileSystem: FileSystem,
	projectsRepository: ProjectsRepository,
	clock: Clock
): ProjectBackupRepository {
	return AndroidProjectBackupRepository(fileSystem, projectsRepository, clock)
}

class AndroidProjectBackupRepository(
	fileSystem: FileSystem,
	projectsRepository: ProjectsRepository,
	clock: Clock
) : ProjectBackupRepository(fileSystem, projectsRepository, clock) {
	override fun supportsBackup() = false

	override suspend fun createBackup(projectDef: ProjectDef): ProjectBackupDef {
		throw NotImplementedError("This platform does not support backups")
	}
}