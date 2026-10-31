package com.darkrockstudios.apps.hammer.common.compose.images

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.image.ImageFormat
import com.darkrockstudios.apps.hammer.common.compose.stageIntoCache
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.images_toast_load_failed
import com.darkrockstudios.apps.hammer.images_toast_name_invalid
import com.darkrockstudios.apps.hammer.images_toast_too_large
import com.darkrockstudios.apps.hammer.images_toast_unsupported
import io.github.aakira.napier.Napier
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitPickerException
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** A picked or dropped image copied where the image service can read it, or why it could not be. */
sealed interface StagedImage {
	data class Staged(val path: String) : StagedImage
	data class Failed(val result: ImageAddResult) : StagedImage
}

/**
 * Asks the user for an image file of a supported type. Null when they cancel; when the picker
 * itself fails, null after [onFailed] is told.
 */
suspend fun pickImageFile(onFailed: suspend (ImageAddResult) -> Unit): PlatformFile? = try {
	FileKit.openFilePicker(type = FileKitType.File(ImageFormat.ALLOWED_EXTENSIONS))
} catch (e: FileKitPickerException) {
	Napier.e("Image picker failed", e)
	onFailed(ImageAddResult.ReadFailed)
	null
}

/** Copies this file into the app cache, refusing one over [ImageFormat.MAX_IMAGE_BYTES] before reading it. */
suspend fun PlatformFile.stageImage(ioDispatcher: CoroutineContext): StagedImage {
	if (size() > ImageFormat.MAX_IMAGE_BYTES) return StagedImage.Failed(ImageAddResult.TooLarge)
	val local = withContext(ioDispatcher) { stageIntoCache() }
		?: return StagedImage.Failed(ImageAddResult.ReadFailed)
	return StagedImage.Staged(local.path)
}

/** The snackbar text for [result], or null when there is nothing to say. */
suspend fun imageAddResultMessage(result: ImageAddResult, strRes: StrRes): String? = when (result) {
	ImageAddResult.Ok -> null
	ImageAddResult.TooLarge -> strRes.get(Res.string.images_toast_too_large, ImageFormat.MAX_IMAGE_MB)
	ImageAddResult.UnsupportedType -> strRes.get(
		Res.string.images_toast_unsupported,
		ImageFormat.ALLOWED_EXTENSIONS.joinToString(),
	)

	ImageAddResult.NameInvalid -> strRes.get(Res.string.images_toast_name_invalid)
	ImageAddResult.ReadFailed -> strRes.get(Res.string.images_toast_load_failed)
}
