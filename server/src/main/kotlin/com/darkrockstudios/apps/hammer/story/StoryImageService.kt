package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.project.ProjectDefinition
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource
import com.darkrockstudios.apps.hammer.utilities.ServerResult

/** Reads Image entities for the web reader. */
class StoryImageService(
	private val projectEntityDatasource: ProjectEntityDatasource,
) {
	/** The stored hash of Image entity [imageId], or null when [imageId] is not an image of [projectDef]. */
	suspend fun imageHash(userId: Long, projectDef: ProjectDefinition, imageId: Int): String? =
		(projectEntityDatasource.loadEntityHash(
			userId = userId,
			projectDef = projectDef,
			entityId = imageId,
			entityType = ApiProjectEntity.Type.IMAGE,
		) as? ServerResult.Success)?.data

	/** Image entity [imageId] ready to serve, or null when it is missing or can't be served. */
	suspend fun loadImage(userId: Long, projectDef: ProjectDefinition, imageId: Int): StoryImage? {
		val image = (projectEntityDatasource.loadEntity(
			userId = userId,
			projectDef = projectDef,
			entityId = imageId,
			entityType = ApiProjectEntity.Type.IMAGE,
			serializer = ApiProjectEntity.ImageEntity.serializer(),
		) as? ServerResult.Success)?.data ?: return null
		val mediaType = ImageFormat.mimeType(image.fileExtension) ?: return null
		val bytes = ImageFormat.decodeBase64(image.base64) ?: return null
		return StoryImage(mediaType, bytes)
	}
}

class StoryImage(
	val mediaType: String,
	val bytes: ByteArray,
)
