package com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers

import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectmetadatarepository.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.projectsync.EntitySynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.projectsync.syncLogE
import com.darkrockstudios.apps.hammer.common.data.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineRepository
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.flow.first

class ClientTimelineSynchronizer(
	projectDef: ProjectDef,
	serverProjectApi: ServerProjectApi,
	projectMetadataDatasource: ProjectMetadataDatasource,
	private val timeLineRepository: TimeLineRepository,
	private val strRes: StrRes,
) : EntitySynchronizer<ApiProjectEntity.TimelineEventEntity>(
	projectDef,
	serverProjectApi,
	projectMetadataDatasource
) {

	override suspend fun prepareForSync() {
		timeLineRepository.loadTimeline()
		// Collect one to make sure its loaded
		timeLineRepository.timelineFlow.first()
	}

	override suspend fun ownsEntity(id: Int): Boolean {
		return timeLineRepository.getTimelineEvent(id) != null
	}

	override suspend fun getEntityHash(id: Int): String? {
		val event = timeLineRepository.getTimelineEvent(id)
		return if (event != null) {
			EntityHasher.hashTimelineEvent(
				id = event.id,
				order = event.order,
				date = event.date,
				content = event.content,
			)
		} else {
			null
		}
	}

	override suspend fun createEntityForId(id: Int): ApiProjectEntity.TimelineEventEntity {
		val event =
			timeLineRepository.getTimelineEvent(id)
				?: throw IllegalStateException("Timeline event $id not found")

		return ApiProjectEntity.TimelineEventEntity(
			id = id,
			order = event.order,
			date = event.date,
			content = event.content
		)
	}

	override suspend fun reIdEntity(oldId: Int, newId: Int) {
		timeLineRepository.reIdEvent(oldId, newId)
	}

	override suspend fun storeEntity(
		serverEntity: ApiProjectEntity.TimelineEventEntity,
		syncId: String,
		onLog: OnSyncLog
	): Boolean {
		val event = TimeLineEvent(
			id = serverEntity.id,
			order = serverEntity.order,
			date = serverEntity.date,
			content = serverEntity.content,
		)

		timeLineRepository.updateEventForSync(event)

		return true
	}

	override suspend fun finalizeSync() {
		timeLineRepository.correctEventOrder()
		timeLineRepository.storeTimeline()
	}

	override fun getEntityType() = EntityType.TimelineEvent

	override suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog) {
		val event = timeLineRepository.getTimelineEvent(id)
		if (event != null) {
			if (timeLineRepository.deleteEvent(event)) {
				onLog(syncLogI(strRes.get(MR.strings.sync_event_deleted, id), projectDef))
			} else {
				onLog(syncLogE(strRes.get(MR.strings.sync_event_delete_failed, id), projectDef))
			}
		} else {
			onLog(
				syncLogE(
					strRes.get(MR.strings.sync_event_delete_failed_not_found, id),
					projectDef
				)
			)
		}
	}

	override suspend fun hashEntities(newIds: List<Int>): Set<EntityHash> {
		return timeLineRepository.timelineFlow.first().events
			.filter { newIds.contains(it.id).not() }
			.mapNotNull { event ->
				getEntityHash(event.id)?.let { hash ->
					EntityHash(event.id, hash)
				}
			}
			.toSet()
	}
}