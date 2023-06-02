package com.darkrockstudios.apps.hammer.common.data.accountsync

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.globalsettings.ServerSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository

class AccountSync(
	private val globalSettingsRepository: GlobalSettingsRepository,
	private val projectsRepository: ProjectsRepository,
) {

	suspend fun sync() {
		val settings = globalSettingsRepository.serverSettings ?: throw IllegalStateException("Server settings missing")

		val dir = projectsRepository.getProjectsDirectory()
		val projects = projectsRepository.getProjects(dir)
		projects.forEach { projectDef ->
			syncProject(projectDef, settings)
		}
	}

	private suspend fun syncProject(projectDef: ProjectDef, settings: ServerSettings) {
		//val synchronizer = ProjectSynchronizer(projectDef, settings)
		//synchronizer.sync()
	}
}