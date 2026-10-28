package com.darkrockstudios.apps.hammer.common.components.images

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageGalleryComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
	mode: ImageGalleryMode,
	private val onDismiss: () -> Unit,
) : ProjectComponentBase(projectDef, componentContext), ImageGallery {

	private val imagesService: ImagesService by projectInject()
	private val imagePickRequests: ImagePickRequests by projectInject()

	private val _state = MutableValue(ImageGallery.State(mode = mode))
	override val state: Value<ImageGallery.State> = _state

	private var usedIds: Set<Int>? = null
	private var delivered = false
	private var busyCount = 0

	override fun onCreate() {
		super.onCreate()
		scope.launch {
			imagesService.imagesFlow.collect { defs -> showImages(defs) }
		}
		scope.launch { refreshUsage() }
	}

	private suspend fun showImages(defs: List<ImageDef>) {
		val images = defs
			.sortedWith(compareBy<ImageDef> { it.name.lowercase() }.thenBy { it.id })
			.map { def ->
				val file = imagesService.imageFile(def)
				ImageGallery.GalleryImage(
					def = def,
					path = file.path.path,
					hash = file.contentHash,
					inUse = usedIds?.contains(def.id),
				)
			}
		withContext(dispatcherMain) {
			_state.getAndUpdate { current ->
				current.copy(
					images = images,
					loaded = true,
					selectedId = current.selectedId?.takeIf { id -> images.any { it.def.id == id } },
				)
			}
		}
	}

	private suspend fun refreshUsage() {
		val used = imagesService.findUsedImageIds()
		usedIds = used
		withContext(dispatcherMain) {
			_state.getAndUpdate { current ->
				current.copy(images = current.images.map { it.copy(inUse = it.def.id in used) })
			}
		}
	}

	override fun select(id: Int?) {
		_state.getAndUpdate { it.copy(selectedId = id) }
	}

	override fun toggleFilterUnused() {
		val filter = !_state.value.filterUnused
		_state.getAndUpdate { it.copy(filterUnused = filter) }
		// Something may have started or stopped using an image since the gallery opened.
		if (filter) scope.launch { refreshUsage() }
	}

	override suspend fun addImage(path: String): ImageAddResult = whileBusy {
		val outcome = imagesService.addFromPath(path)
		outcome.image?.let { added ->
			showImages(imagesService.ensureImagesLoaded())
			withContext(dispatcherMain) { _state.getAndUpdate { it.copy(selectedId = added.id) } }
		}
		outcome.result
	}

	override suspend fun replaceImage(id: Int, path: String): ImageAddResult = whileBusy {
		val outcome = imagesService.replaceFromPath(id, path)
		if (outcome.image != null) showImages(imagesService.ensureImagesLoaded())
		outcome.result
	}

	override fun startRename(id: Int) {
		_state.getAndUpdate { it.copy(renaming = id) }
	}

	override suspend fun rename(id: Int, name: String): ImageAddResult = whileBusy {
		val outcome = imagesService.rename(id, name)
		if (outcome.image != null) {
			showImages(imagesService.ensureImagesLoaded())
			withContext(dispatcherMain) { endRename() }
		}
		outcome.result
	}

	override fun endRename() {
		_state.getAndUpdate { it.copy(renaming = null) }
	}

	override fun requestDelete(id: Int) {
		scope.launch {
			whileBusy {
				val usages = imagesService.findUsages(id)
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(confirmDelete = ImageGallery.DeleteRequest(id, usages)) }
				}
			}
		}
	}

	override fun cancelDelete() {
		_state.getAndUpdate { it.copy(confirmDelete = null) }
	}

	override suspend fun confirmDelete() {
		val request = _state.value.confirmDelete ?: return
		withContext(dispatcherMain) { cancelDelete() }
		whileBusy {
			val result = imagesService.delete(request.id)
			if (result.deleted) {
				showImages(imagesService.ensureImagesLoaded())
				withContext(dispatcherMain) {
					_state.getAndUpdate { if (it.selectedId == request.id) it.copy(selectedId = null) else it }
				}
			}
		}
	}

	override fun pick(id: Int) {
		val mode = _state.value.mode as? ImageGalleryMode.Pick ?: return
		deliver(mode.target, id)
		onDismiss()
	}

	override fun dismiss() {
		(_state.value.mode as? ImageGalleryMode.Pick)?.let { deliver(it.target, null) }
		onDismiss()
	}

	private fun deliver(target: ImagePickTarget, imageId: Int?) {
		if (delivered) return
		delivered = true
		imagePickRequests.deliver(target, imageId)
	}

	override fun onDestroy() {
		// Replaced by another modal, or the project closed: the screen that asked must not wait forever.
		(_state.value.mode as? ImageGalleryMode.Pick)?.let { deliver(it.target, null) }
		super.onDestroy()
	}

	private suspend fun <T> whileBusy(block: suspend () -> T): T {
		withContext(dispatcherMain) { updateBusy(+1) }
		try {
			return withContext(dispatcherDefault) { block() }
		} finally {
			withContext(NonCancellable + dispatcherMain) { updateBusy(-1) }
		}
	}

	// Main thread only.
	private fun updateBusy(delta: Int) {
		busyCount += delta
		_state.getAndUpdate { it.copy(busy = busyCount > 0) }
	}
}
