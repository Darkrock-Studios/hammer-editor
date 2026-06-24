package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.http.synchronizer.EntityHasher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaDatasource
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaRepository
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.EncyclopediaService
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.projectmetadata.ProjectMetadataDatasource
import com.darkrockstudios.apps.hammer.common.data.references.ReferenceRemapper
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogW
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_encyclopedia_deleted
import com.darkrockstudios.apps.hammer.sync_encyclopedia_image_rejected_invalid_extension
import io.github.aakira.napier.Napier
import korlibs.crypto.encoding.Base64
import kotlinx.coroutines.flow.first

class ClientEncyclopediaSynchronizer(
	projectDef: ProjectDef,
	serverProjectApi: ServerProjectApi,
	projectMetadataDatasource: ProjectMetadataDatasource,
	private val strRes: StrRes,
) : EntitySynchronizer<ApiProjectEntity.EncyclopediaEntryEntity>(
	projectDef,
	serverProjectApi,
	projectMetadataDatasource
), ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val encyclopediaRepository: EncyclopediaRepository by projectInject()
	private val encyclopediaService: EncyclopediaService by projectInject()
	private val encyclopediaDatasource: EncyclopediaDatasource by projectInject()
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

	override suspend fun getEntityHash(id: Int): String {
		val entity = createEntityForId(id)

		return EntityHasher.hashEncyclopediaEntry(
			id = entity.id,
			name = entity.name,
			entryType = entity.entryType,
			text = entity.text,
			tags = entity.tags,
			image = entity.image,
			aliases = entity.aliases,
		)
	}

	override suspend fun createEntityForId(id: Int): ApiProjectEntity.EncyclopediaEntryEntity {
		val entry = encyclopediaRepository.loadEntry(id).entry
		val def = entry.toDef(projectDef)

		val DEFAULT_EXTENSION = "jpg"
		val image = if (encyclopediaDatasource.hasEntryImage(def, DEFAULT_EXTENSION)) {
			val imageBytes = encyclopediaDatasource.loadEntryImage(def, DEFAULT_EXTENSION)
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
			aliases = entry.aliases,
		)
	}

	override suspend fun reIdEntity(oldId: Int, newId: Int) {
		encyclopediaRepository.reIdEntry(oldId, newId)
		referenceRemapper.remapEntryReferences(oldId, newId)
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
		val serverDef = EntryDef(
			projectDef = projectDef,
			id = serverEntity.id,
			name = serverEntity.name,
			type = EntryType.fromString(serverEntity.entryType),
		)

		handleImage(oldDef, serverDef, serverEntity, onLog)

		if (oldDef != null) {
			encyclopediaService.updateEntry(
				oldEntryDef = oldDef,
				name = serverEntity.name,
				text = serverEntity.text,
				tags = serverEntity.tags,
				aliases = serverEntity.aliases,
			)
		} else {
			encyclopediaService.createEntry(
				name = serverEntity.name,
				text = serverEntity.text,
				tags = serverEntity.tags,
				type = EntryType.fromString(serverEntity.entryType),
				imagePath = null, // Always pass null here, we wrote the image our selves
				forceId = serverEntity.id,
				aliases = serverEntity.aliases,
			)
		}

		return true
	}

	private suspend fun handleImage(
		oldDef: EntryDef?,
		serverDef: EntryDef,
		serverEntity: ApiProjectEntity.EncyclopediaEntryEntity,
		onLog: OnSyncLog,
	) {
		val image = serverEntity.image
		if (image != null) {
			if (image.fileExtension.lowercase() !in ALLOWED_IMAGE_EXTENSIONS) {
				Napier.w("Skipped synced image for entry ${serverEntity.id}: invalid file extension '${image.fileExtension}'")
				onLog(
					syncLogW(
						strRes.get(Res.string.sync_encyclopedia_image_rejected_invalid_extension, serverEntity.id),
						projectDef
					)
				)
				return
			}
			val imageBytes = Base64.decode(image.base64, url = true)
			encyclopediaDatasource.writeEntryImage(serverDef, imageBytes, image.fileExtension)
		} else if (oldDef != null && encyclopediaDatasource.hasEntryImage(oldDef, "jpg")) {
			// Server reports no image; drop the local one. Raw datasource delete (no
			// sync-marking) since we're applying server state, not making a local edit.
			encyclopediaDatasource.removeEntryImage(oldDef)
		}
	}

	companion object {
		val ALLOWED_IMAGE_EXTENSIONS = setOf(
			"jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif",
		)
	}
}
