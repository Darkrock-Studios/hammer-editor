package com.darkrockstudios.apps.hammer.wear.data

import com.darkrockstudios.apps.hammer.base.ProjectId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The projects whose content is kept on the watch. Keyed by server id so a rename on another
 * device does not drop the subscription.
 */
class SubscribedProjectsRepository(
	private val datasource: WearPrefsDatasource,
) {
	val subscribedProjectIds: Flow<Set<ProjectId>> =
		datasource.subscribedProjectIds.map { ids -> ids.mapTo(mutableSetOf(), ::ProjectId) }

	suspend fun currentSubscriptions(): Set<ProjectId> = subscribedProjectIds.first()

	suspend fun setSubscribed(projectId: ProjectId, subscribed: Boolean) {
		datasource.setSubscribed(projectId.id, subscribed)
	}

	suspend fun clear() {
		datasource.clear()
	}
}
