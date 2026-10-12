package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The projects whose content is kept on the watch. Keyed by project id so a rename on another
 * device does not drop the subscription. A project the server does not have yet can still be
 * subscribed; it syncs once account sync has created it there.
 */
class SubscribedProjectsRepository(
	private val datasource: WearPrefsDatasource,
) {
	val subscribedProjectIds: Flow<Set<ProjectId>> =
		datasource.subscribedProjectIds.map { ids -> ids.mapTo(mutableSetOf(), ::ProjectId) }

	suspend fun currentSubscriptions(): Set<ProjectId> = subscribedProjectIds.first()

	/** The project a capture defaults to, so dictating a note is one tap on a run. */
	suspend fun lastCaptureProjectId(): ProjectId? = datasource.lastCaptureProjectId.first()?.let(::ProjectId)

	suspend fun setSubscribed(projectId: ProjectId, subscribed: Boolean) {
		datasource.setSubscribed(projectId.id, subscribed)
	}

	suspend fun setLastCaptureProject(projectId: ProjectId) {
		datasource.setLastCaptureProjectId(projectId.id)
	}

	/**
	 * Keeps the subscription and last capture target on a project whose id changed. When another
	 * local project still holds [oldId] (a duplicate re-minted at startup), both stay subscribed and
	 * the last capture target stays on the holder of [oldId].
	 */
	suspend fun onProjectIdChanged(oldId: ProjectId, newId: ProjectId, oldIdStillHeld: Boolean) {
		datasource.replaceProjectId(oldId.id, newId.id, keepOld = oldIdStillHeld)
	}

	/** Re-keys this repository whenever [projectsRepository] changes a project's id. */
	fun followProjectIdChanges(projectsRepository: ProjectsRepository) {
		projectsRepository.addProjectIdChangeListener { _, oldId, newId ->
			onProjectIdChanged(oldId, newId, oldIdStillHeld = projectsRepository.findProject(oldId) != null)
		}
	}

	suspend fun clear() {
		datasource.clear()
	}
}
