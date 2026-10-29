package com.darkrockstudios.apps.hammer.common.components.images

import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.serialization.Serializable

/** What a picked image is for; it routes the result back to the screen that asked. */
@Serializable
sealed class ImagePickTarget {
	/** An image to embed in a scene's text. */
	@Serializable
	data class EditorInsert(val sceneId: Int) : ImagePickTarget()

	/** An image for an entry's image list. */
	@Serializable
	data class EntryImage(val entryId: Int) : ImagePickTarget()

	/** An image to embed in a note's text. */
	@Serializable
	data class NoteImage(val noteId: Int) : ImagePickTarget()

	/** An image to embed in a timeline event's text. */
	@Serializable
	data class EventImage(val eventId: Int) : ImagePickTarget()

	@Serializable
	data object ProjectCover : ImagePickTarget()
}

/** [imageId] is null when the pick was cancelled. */
data class ImagePickResult(val target: ImagePickTarget, val imageId: Int?)

/**
 * Project-scoped bus between a screen that wants an image and the gallery that picks it. The
 * project root opens the gallery for each request; the gallery delivers exactly one result per
 * request, null when the user backs out.
 */
class ImagePickRequests {
	private val _requests = MutableSharedFlow<ImagePickTarget>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)
	val requests: SharedFlow<ImagePickTarget> = _requests

	private val _results = MutableSharedFlow<ImagePickResult>(extraBufferCapacity = 16)
	val results: SharedFlow<ImagePickResult> = _results

	fun request(target: ImagePickTarget) {
		_requests.tryEmit(target)
	}

	fun deliver(target: ImagePickTarget, imageId: Int?) {
		_results.tryEmit(ImagePickResult(target, imageId))
	}

	/** Opens the gallery for [target] and suspends until it delivers. */
	suspend fun pick(target: ImagePickTarget): Int? =
		results
			.onSubscription { request(target) }
			.first { it.target == target }
			.imageId

	/** Waits for the result of a pick for [target] already in progress, such as one restored after process death. */
	suspend fun awaitResult(target: ImagePickTarget): Int? =
		results.first { it.target == target }.imageId
}

/** Opens the gallery for [target] and returns the picked image, or null when the pick was cancelled. */
suspend fun ImagePickRequests.pickImage(target: ImagePickTarget, imagesService: ImagesService): ImageDef? {
	val imageId = pick(target) ?: return null
	return findPickedImage(imageId, imagesService)
}

/** The picked image [imageId], or null when it is gone. */
suspend fun findPickedImage(imageId: Int, imagesService: ImagesService): ImageDef? {
	imagesService.ensureImagesLoaded()
	return imagesService.findImage(imageId)
}
