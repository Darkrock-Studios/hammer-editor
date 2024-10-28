package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientEncyclopediaSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientNoteSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneDraftSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientSceneSynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.ClientTimelineSynchronizer
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope

class EntitySynchronizers(projectDef: ProjectDef) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	val sceneSynchronizer: ClientSceneSynchronizer by projectInject()
	val noteSynchronizer: ClientNoteSynchronizer by projectInject()
	val timelineSynchronizer: ClientTimelineSynchronizer by projectInject()
	val encyclopediaSynchronizer: ClientEncyclopediaSynchronizer by projectInject()
	val sceneDraftSynchronizer: ClientSceneDraftSynchronizer by projectInject()

	val synchronizers: Map<EntityType, EntitySynchronizer<out ApiProjectEntity>> by lazy {
		mapOf(
			EntityType.Scene to sceneSynchronizer,
			EntityType.Note to noteSynchronizer,
			EntityType.TimelineEvent to timelineSynchronizer,
			EntityType.EncyclopediaEntry to encyclopediaSynchronizer,
			EntityType.SceneDraft to sceneDraftSynchronizer
		)
	}

	operator fun get(type: EntityType): EntitySynchronizer<*> {
		return when (type) {
			EntityType.Scene -> sceneSynchronizer
			EntityType.Note -> noteSynchronizer
			EntityType.TimelineEvent -> timelineSynchronizer
			EntityType.EncyclopediaEntry -> encyclopediaSynchronizer
			EntityType.SceneDraft -> sceneDraftSynchronizer
		}
	}

	suspend fun findById(entityId: Int): EntitySynchronizer<*>? {
		val type = findEntityType(entityId)
		return synchronizers[type]
	}

	suspend fun handleConflict(conflict: ApiProjectEntity) {
		when (conflict) {

			is ApiProjectEntity.SceneEntity ->
				sceneSynchronizer.conflictResolution.send(conflict)

			is ApiProjectEntity.NoteEntity ->
				noteSynchronizer.conflictResolution.send(conflict)

			is ApiProjectEntity.TimelineEventEntity ->
				timelineSynchronizer.conflictResolution.send(conflict)

			is ApiProjectEntity.EncyclopediaEntryEntity ->
				encyclopediaSynchronizer.conflictResolution.send(conflict)

			is ApiProjectEntity.SceneDraftEntity ->
				sceneDraftSynchronizer.conflictResolution.send(conflict)
		}
	}

	suspend fun reIdEntry(oldId: Int, newId: Int) {
		val type = findEntityType(oldId)
			?: throw IllegalArgumentException("Entity $oldId not found for reId")
		when (type) {
			EntityType.Scene -> sceneSynchronizer.reIdEntity(oldId = oldId, newId = newId)
			EntityType.Note -> noteSynchronizer.reIdEntity(oldId = oldId, newId = newId)
			EntityType.TimelineEvent -> timelineSynchronizer.reIdEntity(
				oldId = oldId,
				newId = newId
			)

			EntityType.EncyclopediaEntry -> encyclopediaSynchronizer.reIdEntity(
				oldId = oldId,
				newId = newId
			)

			EntityType.SceneDraft -> sceneDraftSynchronizer.reIdEntity(oldId = oldId, newId = newId)
		}
	}

	suspend fun clientHasEntity(id: Int): Boolean {
		return findEntityType(id) != null
	}

	suspend fun findEntityType(id: Int): EntityType? =
		synchronizers.keys.firstOrNull { synchronizers[it]?.ownsEntity(id) == true }

	suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog) {
		synchronizers[findEntityType(id)]?.deleteEntityLocal(id, onLog)
	}

	suspend fun getLocalEntityHash(id: Int): String? {
		val type = findEntityType(id)
		return if (type == null) {
			null
		} else {
			when (type) {
				EntityType.Scene -> sceneSynchronizer.getEntityHash(id)
				EntityType.Note -> noteSynchronizer.getEntityHash(id)
				EntityType.TimelineEvent -> timelineSynchronizer.getEntityHash(id)
				EntityType.EncyclopediaEntry -> encyclopediaSynchronizer.getEntityHash(id)
				EntityType.SceneDraft -> sceneDraftSynchronizer.getEntityHash(id)
			}
		}
	}
}