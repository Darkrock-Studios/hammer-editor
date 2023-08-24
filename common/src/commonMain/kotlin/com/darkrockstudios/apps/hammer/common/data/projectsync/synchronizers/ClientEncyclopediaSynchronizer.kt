package com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectsync.EntitySynchronizer
import com.darkrockstudios.apps.hammer.common.data.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.soywiz.krypto.encoding.Base64
import kotlinx.coroutines.flow.first
import okio.FileSystem

class ClientEncyclopediaSynchronizer(
	projectDef: ProjectDef,
	serverProjectApi: ServerProjectApi,
	private val fileSystem: FileSystem
) : EntitySynchronizer<ApiProjectEntity.EncyclopediaEntryEntity>(projectDef, serverProjectApi), ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()

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

	override suspend fun getEntityHash(id: Int): String {
		val entity = createEntityForId(id)

		return EntityHasher.hashEncyclopediaEntry(
			id = entity.id,
			name = entity.name,
			entryType = entity.entryType,
			text = entity.text,
			tags = entity.tags,
			image = entity.image
		)
	}

	override suspend fun createEntityForId(id: Int): ApiProjectEntity.EncyclopediaEntryEntity {
		val entry = encyclopediaRepository.loadEntry(id).entry
		val def = entry.toDef(projectDef)

		val DEFAULT_EXTENSION = "jpg"
		val image = if (encyclopediaRepository.hasEntryImage(def, DEFAULT_EXTENSION)) {
			val imageBytes = encyclopediaRepository.loadEntryImage(def, DEFAULT_EXTENSION)
			val imageBase64 = Base64.encode(imageBytes, url = true)

			ApiProjectEntity.EncyclopediaEntryEntity.Image(
				base64 = imageBase64,
				fileExtension = DEFAULT_EXTENSION,
			)
		} else {
			null
		}

		return ApiProjectEntity.EncyclopediaEntryEntity(
			id = id,
			name = entry.name,
			entryType = entry.type.text,
			text = entry.text,
			tags = entry.tags,
			image = image,
		)
	}

	override suspend fun reIdEntity(oldId: Int, newId: Int) {
		encyclopediaRepository.reIdEntry(oldId, newId)
	}

	override suspend fun finalizeSync() {
		encyclopediaRepository.loadEntriesImperative()
	}

	override fun getEntityType() = EntityType.EncyclopediaEntry

	override suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog) {
		val def = encyclopediaRepository.getEntryDef(id)
		encyclopediaRepository.deleteEntry(def)

		onLog(syncLogI("Deleted Encyclopedia ID $id from client", def.projectDef.name))
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
		val serverDef = EntryDef(
			projectDef = projectDef,
			id = serverEntity.id,
			name = serverEntity.name,
			type = EntryType.fromString(serverEntity.entryType),
		)

		handleImage(oldDef, serverDef, serverEntity)

		if (oldDef != null) {
			encyclopediaRepository.updateEntry(
				oldEntryDef = oldDef,
				name = serverEntity.name,
				text = serverEntity.text,
				tags = serverEntity.tags,
			)
		} else {
			encyclopediaRepository.createEntry(
				name = serverEntity.name,
				text = serverEntity.text,
				tags = serverEntity.tags,
				type = EntryType.fromString(serverEntity.entryType),
				imagePath = null, // Always pass null here, we wrote the image our selves
				forceId = serverEntity.id
			)
		}

		return true
	}

	private fun handleImage(
		oldDef: EntryDef?,
		serverDef: EntryDef,
		serverEntity: ApiProjectEntity.EncyclopediaEntryEntity
	) {
		// Write the new image
		val image = serverEntity.image
		if (image != null) {
			val imageBytes = Base64.decode(image.base64, url = true)
			val imagePath = encyclopediaRepository.getEntryImagePath(serverDef, image.fileExtension)
			fileSystem.write(imagePath.toOkioPath()) {
				write(imageBytes)
			}
		} else {
			// Delete the old image, if there is a new one it'll get written regardless
			if (oldDef != null) {
				val oldImagePath = encyclopediaRepository.getEntryImagePath(oldDef, "jpg")
				fileSystem.delete(oldImagePath.toOkioPath(), false)
			}
		}
	}
}