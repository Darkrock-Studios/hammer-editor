package com.darkrockstudios.apps.hammer.common.data.imagesrepository

import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef

enum class ImageAddResult {
	Ok,
	TooLarge,
	UnsupportedType,

	/** The image bytes could not be read or written. */
	ReadFailed,
	NameInvalid,
}

/** [image] is set exactly when [result] is [ImageAddResult.Ok]. */
data class ImageAddOutcome(
	val result: ImageAddResult,
	val image: ImageDef? = null,
)
