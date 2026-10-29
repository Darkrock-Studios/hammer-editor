package com.darkrockstudios.apps.hammer.common.components.images

import com.arkivanov.essenty.statekeeper.StateKeeper
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.coroutines.CoroutineContext

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

/**
 * One screen's pick from the gallery, at most one at a time, kept across process death under [key]
 * in [stateKeeper]. The gallery comes back with the project root after process death, so a restored
 * pick waits for its answer again. [onInProgressChange] runs on the main dispatcher.
 */
class ImagePickSession(
	private val requests: Lazy<ImagePickRequests>,
	private val stateKeeper: StateKeeper,
	private val key: String,
	private val scope: CoroutineScope,
	private val mainDispatcher: CoroutineContext,
	private val onInProgressChange: (Boolean) -> Unit = {},
) {
	var inProgress: Boolean = false
		private set

	/**
	 * Call once while the component is created: registers the pick with the state keeper and, when
	 * one was in progress before process death, hands its result for [target] to [onPicked].
	 */
	fun restore(target: ImagePickTarget, onPicked: suspend (imageId: Int) -> Unit) {
		val pending = stateKeeper.consume(key, Boolean.serializer()) == true
		stateKeeper.register(key, Boolean.serializer()) { inProgress }
		if (pending) await(onPicked) { requests.value.awaitResult(target) }
	}

	/** Opens the gallery for [target] and hands the picked image to [onPicked]; ignored while a pick is in progress. */
	fun start(target: ImagePickTarget, onPicked: suspend (imageId: Int) -> Unit) {
		if (inProgress) return
		await(onPicked) { requests.value.pick(target) }
	}

	private fun await(onPicked: suspend (imageId: Int) -> Unit, result: suspend () -> Int?) {
		setInProgress(true)
		scope.launch {
			try {
				val imageId = result() ?: return@launch
				onPicked(imageId)
			} finally {
				withContext(NonCancellable + mainDispatcher) { setInProgress(false) }
			}
		}
	}

	private fun setInProgress(value: Boolean) {
		inProgress = value
		onInProgressChange(value)
	}
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
