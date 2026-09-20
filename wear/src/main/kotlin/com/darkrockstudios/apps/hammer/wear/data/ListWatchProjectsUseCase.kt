package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import okio.IOException

/**
 * A project as the watch sees it. [projectId] is null until account sync has registered the
 * project with the server, and such a project cannot be subscribed to yet.
 */
data class WatchProject(
	val projectDef: ProjectDef,
	val projectId: ProjectId?,
	val subscribed: Boolean,
)

class ListWatchProjectsUseCase(
	private val projectsRepository: ProjectsRepository,
	private val subscriptions: SubscribedProjectsRepository,
) {
	suspend fun list(): List<WatchProject> {
		val subscribed = subscriptions.currentSubscriptions()
		return projectsRepository.getProjects()
			.mapNotNull { projectDef ->
				// The project can be deleted concurrently by a sync.
				val projectId = try {
					projectsRepository.getProjectId(projectDef)
				} catch (_: IOException) {
					return@mapNotNull null
				}
				WatchProject(
					projectDef = projectDef,
					projectId = projectId,
					subscribed = projectId != null && projectId in subscribed,
				)
			}
			.sortedBy { it.projectDef.name.lowercase() }
	}
}
