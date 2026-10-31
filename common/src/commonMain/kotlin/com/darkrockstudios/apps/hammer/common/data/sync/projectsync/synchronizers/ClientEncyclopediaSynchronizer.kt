package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceRemapper
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_encyclopedia_deleted
import kotlinx.coroutines.flow.first

class ClientEncyclopediaSynchronizer(
	projectDef: ProjectDef,
	serverProjectApi: ServerProjectApi,
	private val strRes: StrRes,
) : EntitySynchronizer<ApiProjectEntity.EncyclopediaEntryEntity>(
	projectDef,
	serverProjectApi,
), ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	private val encyclopediaService: EncyclopediaService by projectInject()
	private val referenceRemapper: ReferenceRemapper by projectInject()

	private suspend fun getEntity(id: Int): EntryDef? {
		val entries = encyclopediaRepository.entryListFlow.first()
		return entries.firstOrNull { it.id == id }
	}

	override suspend fun prepareForSync() {
		encyclopediaRepository.loadEntriesImperative()
	}

	override suspend fun ownsEntity(id: Int): Boolean {
		return getEntity(id) != null
	}

	override suspend fun getEntityHash(id: Int): String = createEntityForId(id).hash()

	override suspend fun createEntityForId(id: Int): ApiProjectEntity.EncyclopediaEntryEntity =
		encyclopediaRepository.loadEntry(id).entry.toApiEntity()

	override suspend fun reIdEntity(oldId: Int, newId: Int): Set<Int> {
		encyclopediaRepository.reIdEntry(oldId, newId)
		return referenceRemapper.remapEntryReferences(oldId, newId)
	}

	override suspend fun finalizeSync() {
		encyclopediaRepository.loadEntriesImperative()
	}

	override fun getEntityType() = EntityType.EncyclopediaEntry

	override suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog) {
		val def = encyclopediaRepository.getEntryDef(id)
		encyclopediaService.deleteEntry(def)

		onLog(syncLogI(strRes.get(Res.string.sync_encyclopedia_deleted, id), def.projectDef.name))
	}

	override suspend fun hashEntities(newIds: List<Int>): Set<EntityHash> {
		return encyclopediaRepository.entryListFlow.first()
			.filter { newIds.contains(it.id).not() }
			.map { entry ->
				val hash = getEntityHash(entry.id)
				EntityHash(entry.id, hash)
			}
			.toSet()
	}

	override suspend fun storeEntity(
		serverEntity: ApiProjectEntity.EncyclopediaEntryEntity,
		syncId: String,
		onLog: OnSyncLog
	): Boolean {
		val oldDef = encyclopediaRepository.findEntryDef(serverEntity.id)

		// Image ids are stored as given: an image the entry lists may not have arrived yet.
		if (oldDef != null) {
			encyclopediaService.updateEntry(
				oldEntryDef = oldDef,
				name = serverEntity.name,
				text = serverEntity.text,
				tags = serverEntity.tags,
				aliases = serverEntity.aliases,
				excludeFromDictionary = serverEntity.excludeFromDictionary,
				images = serverEntity.images,
			)
		} else {
			encyclopediaService.createEntry(
				name = serverEntity.name,
				text = serverEntity.text,
				tags = serverEntity.tags,
				type = EntryType.fromString(serverEntity.entryType),
				imagePath = null,
				forceId = serverEntity.id,
				aliases = serverEntity.aliases,
				excludeFromDictionary = serverEntity.excludeFromDictionary,
				images = serverEntity.images,
			)
		}

		return true
	}
}
