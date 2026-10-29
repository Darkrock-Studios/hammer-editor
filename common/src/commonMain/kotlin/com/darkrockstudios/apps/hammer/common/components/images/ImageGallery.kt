package com.darkrockstudios.apps.hammer.common.components.images

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.references.ImageUsages
import kotlinx.serialization.Serializable

@Serializable
sealed class ImageGalleryMode {
	@Serializable
	data object Browse : ImageGalleryMode()

	/** The gallery was opened to choose an image for [target]. */
	@Serializable
	data class Pick(val target: ImagePickTarget) : ImageGalleryMode()
}

interface ImageGallery {
	val state: Value<State>

	data class State(
		val mode: ImageGalleryMode,
		val images: List<GalleryImage> = emptyList(),
		/** False until the project's images have been listed once. */
		val loaded: Boolean = false,
		val filterUnused: Boolean = false,
		/** Ids are allocated in increasing order, so id order is the order images were added in. */
		val newestFirst: Boolean = true,
		/** Matched case-insensitively against image names; blank shows every image. */
		val searchQuery: String = "",
		val selectedId: Int? = null,
		/** The image whose rename dialog is open. */
		val renaming: Int? = null,
		val confirmDelete: DeleteRequest? = null,
		val busy: Boolean = false,
	) {
		/**
		 * [images] after the unused filter and the search, in sort order. An image whose use is not
		 * yet known passes the unused filter.
		 */
		val visibleImages: List<GalleryImage>
			get() {
				val query = searchQuery.trim()
				val shown = images.filter { image ->
					(!filterUnused || image.inUse != true) &&
						(query.isEmpty() || image.def.name.contains(query, ignoreCase = true))
				}
				return if (newestFirst) shown.sortedByDescending { it.def.id } else shown.sortedBy { it.def.id }
			}

		val selected: GalleryImage? get() = images.find { it.def.id == selectedId }
	}

	/** [cacheToken] changes whenever the image's bytes do, so it keys image caches. [inUse] is null until known. */
	data class GalleryImage(
		val def: ImageDef,
		val path: String,
		val cacheToken: String,
		val inUse: Boolean?,
	)

	data class DeleteRequest(val id: Int, val usages: ImageUsages)

	fun select(id: Int?)
	fun toggleFilterUnused()
	fun toggleSortOrder()
	fun setSearchQuery(query: String)

	/** Adds the file at [path] as a new image and selects it. */
	suspend fun addImage(path: String): ImageAddResult

	/** Replaces image [id]'s bytes with the file at [path]; it keeps its id, so every reference follows. */
	suspend fun replaceImage(id: Int, path: String): ImageAddResult

	fun startRename(id: Int)
	suspend fun rename(id: Int, name: String): ImageAddResult
	fun endRename()

	/** Finds what uses image [id], then asks to confirm its deletion. */
	fun requestDelete(id: Int)
	fun cancelDelete()

	/** Deletes the image [State.confirmDelete] names, taking it out of everything that uses it. */
	suspend fun confirmDelete()

	/** In pick mode, hands image [id] to the screen that asked and closes the gallery. */
	fun pick(id: Int)

	/** Closes the gallery; in pick mode the screen that asked gets no image. */
	fun dismiss()
}
