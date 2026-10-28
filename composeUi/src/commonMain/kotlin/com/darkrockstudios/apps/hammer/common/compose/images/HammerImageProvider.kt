package com.darkrockstudios.apps.hammer.common.compose.images

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferenceSyntax
import com.darkrockstudios.texteditor.richstyle.ImageBlockResource
import com.darkrockstudios.texteditor.richstyle.ImageProvider
import io.github.aakira.napier.Napier
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** Decodes the image file at [path]; [version] changes whenever the file's bytes do. Null when it cannot. */
fun interface ImageBitmapLoader {
	suspend fun load(path: String, version: String): ImageBitmap?
}

/**
 * Resolves the editor's `hammer://image/<id>` image sources to the project's Image entities. Every
 * other source fails: the editor loads nothing from the network.
 *
 * [resolve] runs while the editor measures and draws, so it only hands back a state per id and
 * starts a load the first time an id is asked for. A changed image (replaced, renamed, re-IDed or
 * deleted) is loaded again, and its old bitmap stays on show until the new one is ready.
 */
class HammerImageProvider(
	private val imagesService: ImagesService,
	private val bitmapLoader: ImageBitmapLoader,
	dispatcher: CoroutineContext = Dispatchers.Default,
) : ImageProvider, AutoCloseable {

	private val scope = CoroutineScope(SupervisorJob() + dispatcher)
	private val lock = reentrantLock()
	private val entries = mutableMapOf<Int, Entry>()
	private val unresolvable = mutableMapOf<String, State<ImageBlockResource>>()

	private class Entry(val state: MutableState<ImageBlockResource>) {
		/** Path and version of the bitmap in [state], so an unchanged image is not decoded again. */
		var loadedKey: String? = null
		var job: Job? = null

		/** Bumped per load, so a superseded load that finishes late does not overwrite a newer one. */
		var generation = 0
	}

	init {
		scope.launch {
			imagesService.imageChangedFlow.collect { id ->
				lock.withLock { entries[id] }?.let { load(id, it) }
			}
		}
	}

	override fun resolve(source: String): State<ImageBlockResource> {
		val id = ImageReferenceSyntax.parseId(source) ?: return lock.withLock {
			unresolvable.getOrPut(source) { mutableStateOf(ImageBlockResource.Failed("not a project image: $source")) }
		}
		var created: Entry? = null
		val entry = lock.withLock {
			entries.getOrPut(id) { Entry(mutableStateOf(ImageBlockResource.Loading)).also { created = it } }
		}
		created?.let { load(id, it) }
		return entry.state
	}

	private fun load(id: Int, entry: Entry) {
		lock.withLock {
			entry.job?.cancel()
			val generation = ++entry.generation
			entry.job = scope.launch { loadNow(id, entry, generation) }
		}
	}

	@Suppress("TooGenericExceptionCaught")
	private suspend fun loadNow(id: Int, entry: Entry, generation: Int) {
		val loaded: Pair<String?, ImageBlockResource> = try {
			imagesService.ensureImagesLoaded()
			val def = imagesService.findImage(id)
			val stamp = def?.let(imagesService::fileStamp)
			if (def == null || stamp == null) {
				null to ImageBlockResource.Failed("missing image $id")
			} else {
				val version = imagesService.cachedContentHash(def, stamp)
					?: "stamp:${stamp.size}-${stamp.lastModifiedAtMillis}"
				val path = imagesService.imagePath(def).path
				val key = "$path#$version"
				if (key == lock.withLock { entry.loadedKey }) return
				val bitmap = bitmapLoader.load(path, version)
				if (bitmap == null) {
					null to ImageBlockResource.Failed("could not load image $id")
				} else {
					key to ImageBlockResource.Loaded(bitmap)
				}
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Napier.w("Failed to load image $id for the editor", e)
			null to ImageBlockResource.Failed("could not load image $id")
		}
		lock.withLock {
			if (entry.generation != generation) return
			entry.loadedKey = loaded.first
			entry.state.value = loaded.second
		}
	}

	override fun close() {
		scope.cancel()
	}
}
