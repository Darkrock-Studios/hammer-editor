package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository

/** A project as the watch sees it. */
data class WatchProject(
	val projectDef: ProjectDef,
	val subscribed: Boolean,
)

class ListWatchProjectsUseCase(
	private val projectsRepository: ProjectsRepository,
	private val subscriptions: SubscribedProjectsRepository,
) {
	suspend fun list(): List<WatchProject> {
		val subscribed = subscriptions.currentSubscriptions()
		return projectsRepository.getProjects()
			.map { projectDef ->
				WatchProject(
					projectDef = projectDef,
					subscribed = projectDef.id in subscribed,
				)
			}
			.sortedBy { it.projectDef.name.lowercase() }
	}
}
