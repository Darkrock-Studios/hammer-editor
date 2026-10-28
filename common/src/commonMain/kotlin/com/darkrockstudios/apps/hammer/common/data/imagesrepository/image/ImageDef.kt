package com.darkrockstudios.apps.hammer.common.data.imagesrepository.image

/** An Image entity as it is stored on disk: `images/<id>~<name>.<fileExtension>`. */
data class ImageDef(
	val id: Int,
	val name: String,
	val fileExtension: String,
)
