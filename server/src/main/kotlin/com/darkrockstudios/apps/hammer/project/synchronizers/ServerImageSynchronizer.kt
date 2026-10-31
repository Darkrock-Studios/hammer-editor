package com.darkrockstudios.apps.hammer.project.synchronizers

import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.base.image.ImageProblem
import com.darkrockstudios.apps.hammer.project.EntityTooLargeException
import com.darkrockstudios.apps.hammer.project.EntityValidationException
import com.darkrockstudios.apps.hammer.project.ProjectEntityDatasource

class ServerImageSynchronizer(
	datasource: ProjectEntityDatasource,
) : ServerEntitySynchronizer<ApiProjectEntity.ImageEntity>(datasource) {
	override val entityType = ApiProjectEntity.Type.IMAGE
	override val entityClazz = ApiProjectEntity.ImageEntity::class
	override val pathStub = ApiProjectEntity.Type.IMAGE.name.lowercase()

	override fun validate(entity: ApiProjectEntity.ImageEntity): Exception? {
		val bytes = ImageFormat.decodeBase64(entity.base64)
			?: return EntityValidationException(entity.id, "image data is not valid base64")

		return when (ImageFormat.validate(entity.name, entity.fileExtension, bytes)) {
			null -> null
			ImageProblem.InvalidName -> EntityValidationException(entity.id, "invalid image name")
			ImageProblem.UnsupportedType ->
				EntityValidationException(entity.id, "unsupported image type '${entity.fileExtension}'")

			ImageProblem.TooLarge ->
				EntityTooLargeException(entity.id, bytes.size, ImageFormat.MAX_IMAGE_BYTES.toInt())

			ImageProblem.ContentMismatch ->
				EntityValidationException(entity.id, "image data does not match type '${entity.fileExtension}'")
		}
	}
}
