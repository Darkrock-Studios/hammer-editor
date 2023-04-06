package com.darkrockstudios.apps.hammer.common.data.projectsync

import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.server.ServerProjectsApi
import io.github.aakira.napier.Napier


class ClientProjectsSynchronizer(
	private val projectsRepository: ProjectsRepository,
	private val serverProjectsApi: ServerProjectsApi
) {
	suspend fun syncProjects() {
		val result = serverProjectsApi.getProjects()
		if (result.isSuccess) {
			val serverProjects = result.getOrThrow().projects
			val localProjects = projectsRepository.getProjects()

			val newProjects = serverProjects.filter { serverProject ->
				localProjects.none { localProject -> localProject.name == serverProject }
			}

			Napier.i("New projects: ${newProjects.size}")

			newProjects.forEach { projectName ->
				projectsRepository.createProject(projectName)
			}

			Napier.i("Synced projects: ${serverProjects.size}")
		} else {
			Napier.w("Failed to sync projects: ${result.exceptionOrNull()?.message}")
		}
	}
}