package com.darkrockstudios.apps.hammer.common.data.projectbackup

import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import okio.FileSystem
import kotlin.time.Clock

actual fun createProjectBackup(
	fileSystem: FileSystem,
	projectsRepository: ProjectsRepository,
	clock: Clock
): ProjectBackupRepository {
	return DesktopProjectBackupRepository(fileSystem, projectsRepository, clock)
}