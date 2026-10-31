package com.darkrockstudios.apps.hammer.common.data.sync.projectsync.synchronizers

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.EntityHash
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesRepository
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceRewriter
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.EntitySynchronizer
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.OnSyncLog
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogI
import com.darkrockstudios.apps.hammer.common.data.sync.projectsync.syncLogW
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.server.ServerProjectApi
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.sync_image_deleted
import com.darkrockstudios.apps.hammer.sync_image_rejected_invalid
import io.github.aakira.napier.Napier
import korlibs.crypto.encoding.Base64

class ClientImageSynchronizer(
	projectDef: ProjectDef,
	serverProjectApi: ServerProjectApi,
	private val strRes: StrRes,
) : EntitySynchronizer<ApiProjectEntity.ImageEntity>(
	projectDef,
	serverProjectApi,
), ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val imagesRepository: ImagesRepository by projectInject()
	private val referenceRewriter: ImageReferenceRewriter by projectInject()

	override suspend fun prepareForSync() {
		imagesRepository.loadImages()
	}

	// The list prepareForSync loaded; every write through the repository reloads it.
	private fun loadedImage(id: Int): ImageDef? = imagesRepository.getImages().firstOrNull { it.id == id }

	override suspend fun ownsEntity(id: Int): Boolean = loadedImage(id) != null

	override suspend fun getEntityHash(id: Int): String? = loadedImage(id)?.let { imagesRepository.contentHash(it) }

	override suspend fun createEntityForId(id: Int): ApiProjectEntity.ImageEntity {
		val def = loadedImage(id) ?: error("Image $id not found")
		val bytes = imagesRepository.loadImageBytes(def) ?: error("Failed to read image $id")
		return ApiProjectEntity.ImageEntity(
			id = def.id,
			name = def.name,
			fileExtension = def.fileExtension,
			base64 = Base64.encode(bytes, url = true),
		)
	}

	override suspend fun storeEntity(
		serverEntity: ApiProjectEntity.ImageEntity,
		syncId: String,
		onLog: OnSyncLog
	): Boolean {
		val bytes = decodeValid(serverEntity)
		if (bytes == null) {
			Napier.w("Rejected synced image ${serverEntity.id}: invalid name, extension or content")
			onLog(syncLogW(strRes.get(Res.string.sync_image_rejected_invalid, serverEntity.id), projectDef))
			return false
		}

		val def = ImageDef(
			id = serverEntity.id,
			name = serverEntity.name,
			fileExtension = serverEntity.fileExtension,
		)
		return imagesRepository.storeSyncImage(def, bytes)
	}

	// The name and extension become a filename, and the bytes are rendered.
	private fun decodeValid(entity: ApiProjectEntity.ImageEntity): ByteArray? {
		val bytes = ImageFormat.decodeBase64(entity.base64) ?: return null
		return bytes.takeIf { ImageFormat.validate(entity.name, entity.fileExtension, bytes) == null }
	}

	// The file moves first so a rewritten reference never points at an id with no file.
	override suspend fun reIdEntity(oldId: Int, newId: Int): Set<Int> {
		imagesRepository.reIdImage(oldId, newId)
		return referenceRewriter.remap(mapOf(oldId to newId)).mapTo(mutableSetOf()) { it.id }
	}

	override suspend fun finalizeSync() {
		imagesRepository.loadImages()
	}

	override fun getEntityType() = EntityType.Image

	// Only the file: the device that deleted the image already removed its references.
	override suspend fun deleteEntityLocal(id: Int, onLog: OnSyncLog) {
		imagesRepository.deleteSyncImage(id)
		onLog(syncLogI(strRes.get(Res.string.sync_image_deleted, id), projectDef))
	}

	override suspend fun hashEntities(newIds: List<Int>): Set<EntityHash> {
		return imagesRepository.loadImages()
			.filter { newIds.contains(it.id).not() }
			.mapNotNull { def ->
				imagesRepository.contentHash(def)?.let { hash -> EntityHash(def.id, hash) }
			}
			.toSet()
	}
}
