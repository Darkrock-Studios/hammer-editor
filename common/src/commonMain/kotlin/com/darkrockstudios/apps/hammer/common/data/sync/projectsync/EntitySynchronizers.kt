package com.darkrockstudios.apps.hammer.common.data.sync.projectsync

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers.*
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

class EntitySynchronizers(private val projectDef: ProjectDef) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)

	val sceneSynchronizer: ClientSceneSynchronizer by projectInject()
	val noteSynchronizer: ClientNoteSynchronizer by projectInject()
	val timelineSynchronizer: ClientTimelineSynchronizer by projectInject()
	val encyclopediaSynchronizer: ClientEncyclopediaSynchronizer by projectInject()
	val sceneDraftSynchronizer: ClientSceneDraftSynchronizer by projectInject()
	val imageSynchronizer: ClientImageSynchronizer by projectInject()

	val synchronizers: Map<EntityType, EntitySynchronizer<out ApiProjectEntity>> by lazy {
		mapOf(
			EntityType.Scene to sceneSynchronizer,
			EntityType.Note to noteSynchronizer,
			EntityType.TimelineEvent to timelineSynchronizer,
			EntityType.EncyclopediaEntry to encyclopediaSynchronizer,
			EntityType.SceneDraft to sceneDraftSynchronizer,
			EntityType.Image to imageSynchronizer,
		)
	}

	operator fun get(type: EntityType): EntitySynchronizer<*> {
		return when (type) {
			EntityType.Scene -> sceneSynchronizer
			EntityType.Note -> noteSynchronizer
			EntityType.TimelineEvent -> timelineSynchronizer
			EntityType.EncyclopediaEntry -> encyclopediaSynchronizer
			EntityType.SceneDraft -> sceneDraftSynchronizer
			EntityType.Image -> imageSynchronizer
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

			is ApiProjectEntity.ImageEntity ->
				imageSynchronizer.conflictResolution.send(conflict)
		}
	}

	/** Gives the entity [oldId] the id [newId]; what changed, or null when no entity has [oldId]. */
	suspend fun reIdEntry(oldId: Int, newId: Int): EntityIdChange? {
		val type = findEntityType(oldId)
		if (type == null) {
			// Phantom newId with no backing entity (created then deleted before a
			// successful sync). Skip it so the sync can finish and clear newIds,
			// instead of wedging every future sync.
			Napier.w("reId skipped: no local entity owns $oldId")
			return null
		}
		val rewrittenReferrers = when (type) {
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
			EntityType.Image -> imageSynchronizer.reIdEntity(oldId = oldId, newId = newId)
		}
		return EntityIdChange(type, oldId, newId, rewrittenReferrers)
	}

	/** Tells every [EntityIdChangeListener] bound in Koin of a sync's [changes], unless there are none. */
	@Suppress("TooGenericExceptionCaught") // The entities have their new ids already, so a failing listener must not fail the sync.
	suspend fun notifyEntityIdsChanged(changes: List<EntityIdChange>) {
		if (changes.isEmpty()) return
		getKoin().getAll<EntityIdChangeListener>().forEach { listener ->
			try {
				listener.onEntityIdsChanged(projectDef, changes)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Napier.e("Entity id change listener failed for $changes", e)
			}
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
				EntityType.Image -> imageSynchronizer.getEntityHash(id)
			}
		}
	}
}