package com.darkrockstudios.apps.hammer.common.data.projectsync.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHash
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectsync.EntitySynchronizer
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
		encyclopediaRepository.loadEntriesImperetive()
	}

	override suspend fun ownsEntity(id: Int): Boolean {
		return getEntity(id) != null
	}

	override suspend fun getEntityHash(id: Int): String {
		val entity = createEntityForId(id)

		return EntityHash.hashEncyclopediaEntry(
			id = entity.id,
			name = entity.name,
			entityType = entity.type.name,
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
			entryType = entry.type.name,
			text = entry.text,
			tags = entry.tags,
			image = image,
		)
	}

	override suspend fun reIdEntity(oldId: Int, newId: Int) {
		encyclopediaRepository.reIdEntry(oldId, newId)
	}

	override suspend fun finalizeSync() {
		encyclopediaRepository.loadEntriesImperetive()
	}

	override fun getEntityType() = EntityType.EncyclopediaEntry

	override suspend fun storeEntity(
		serverEntity: ApiProjectEntity.EncyclopediaEntryEntity,
		syncId: String,
		onLog: suspend (String?) -> Unit
	) {
		val oldDef = encyclopediaRepository.getEntryDef(serverEntity.id)
		val serverDef = EntryDef(
			projectDef = projectDef,
			id = serverEntity.id,
			name = serverEntity.name,
			type = EntryType.fromString(serverEntity.entryType),
		)

		val oldImagePath = encyclopediaRepository.getEntryImagePath(oldDef, "jpg")
		fileSystem.delete(oldImagePath.toOkioPath(), false)

		val image = serverEntity.image
		if (image != null) {
			val imageBytes = Base64.decode(image.base64, url = true)
			val imagePath = encyclopediaRepository.getEntryImagePath(serverDef, image.fileExtension)
			fileSystem.write(imagePath.toOkioPath()) {
				write(imageBytes)
			}
		}

		val entryPath = encyclopediaRepository.getEntryPath(serverEntity.id)
		if (fileSystem.exists(entryPath.toOkioPath())) {
			encyclopediaRepository.updateEntry(
				oldEntryDef = oldDef,
				name = serverEntity.name,
				text = serverEntity.text,
				tags = serverEntity.tags,
			)
		} else {
			val imagePath = if (image != null) {
				encyclopediaRepository.getEntryImagePath(serverDef, image.fileExtension).path
			} else {
				null
			}
			encyclopediaRepository.createEntry(
				name = serverEntity.name,
				text = serverEntity.text,
				tags = serverEntity.tags,
				type = EntryType.fromString(serverEntity.entryType),
				imagePath = imagePath,
			)
		}
	}
}