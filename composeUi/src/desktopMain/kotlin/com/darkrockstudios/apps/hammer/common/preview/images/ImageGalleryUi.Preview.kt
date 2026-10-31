package com.darkrockstudios.apps.hammer.common.preview.images

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.update
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.components.images.ImageGallery
import com.darkrockstudios.apps.hammer.common.components.images.ImageGalleryMode
import com.darkrockstudios.apps.hammer.common.components.images.ImagePickTarget
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImageAddResult
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.references.ImageReferrer
import com.darkrockstudios.apps.hammer.common.data.references.ImageUsage
import com.darkrockstudios.apps.hammer.common.data.references.ImageUsages
import com.darkrockstudios.apps.hammer.common.data.references.TouchedEntity
import com.darkrockstudios.apps.hammer.common.images.ImageGalleryUi
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.TABLET_TALL_HEIGHT_DP
import com.darkrockstudios.apps.hammer.common.preview.TABLET_WIDTH_DP

/** An [ImageGallery] that records what it is asked to do; [usages] answers [requestDelete]. */
class FakeImageGallery(
	initial: ImageGallery.State,
	private val usages: Map<Int, ImageUsages> = emptyMap(),
) : ImageGallery {
	private val _state = MutableValue(initial)
	override val state: Value<ImageGallery.State> = _state

	val picked = mutableListOf<Int>()
	var dismissed = 0
		private set

	override fun select(id: Int?) = _state.update { it.copy(selectedId = id) }
	override fun toggleFilterUnused() = _state.update { it.copy(filterUnused = !it.filterUnused) }
	override fun toggleSortOrder() = _state.update { it.copy(newestFirst = !it.newestFirst) }
	override fun setSearchQuery(query: String) = _state.update { it.copy(searchQuery = query) }
	override suspend fun addImage(path: String) = ImageAddResult.Ok
	override suspend fun replaceImage(id: Int, path: String) = ImageAddResult.Ok
	override fun startRename(id: Int) = _state.update { it.copy(renaming = id) }
	override suspend fun rename(id: Int, name: String) = ImageAddResult.Ok
	override fun endRename() = _state.update { it.copy(renaming = null) }

	override fun requestDelete(id: Int) = _state.update {
		it.copy(confirmDelete = ImageGallery.DeleteRequest(id, usages[id] ?: ImageUsages(emptyList())))
	}

	override fun cancelDelete() = _state.update { it.copy(confirmDelete = null) }
	override suspend fun confirmDelete() = cancelDelete()

	override fun pick(id: Int) {
		picked += id
	}

	override fun dismiss() {
		dismissed++
	}
}

fun previewGalleryImages(): List<ImageGallery.GalleryImage> = listOf(
	ImageGallery.GalleryImage(ImageDef(3, "Harbour at dusk", "jpg"), "/preview/3.jpg", "h3", inUse = true),
	ImageGallery.GalleryImage(ImageDef(5, "Lighthouse", "png"), "/preview/5.png", "h5", inUse = false),
	ImageGallery.GalleryImage(ImageDef(8, "Map of the coast", "webp"), "/preview/8.webp", "h8", inUse = true),
	ImageGallery.GalleryImage(ImageDef(13, "Storm", "jpeg"), "/preview/13.jpeg", "h13", inUse = null),
)

fun previewImageUsages(): Map<Int, ImageUsages> = mapOf(
	3 to ImageUsages(
		listOf(
			ImageUsage(ImageReferrer.Entity(TouchedEntity(EntityType.EncyclopediaEntry, 30)), "Alice"),
			ImageUsage(ImageReferrer.Entity(TouchedEntity(EntityType.EncyclopediaEntry, 31)), "The Point"),
			ImageUsage(ImageReferrer.ProjectCover, "Alice in Wonderland"),
		)
	),
)

@Preview
@Composable
fun ImageGalleryBrowsePreview() {
	KoinApplicationPreview {
		ImageGalleryUi(
			FakeImageGallery(
				ImageGallery.State(
					mode = ImageGalleryMode.Browse,
					images = previewGalleryImages(),
					loaded = true,
					selectedId = 3,
				)
			)
		)
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_TALL_HEIGHT_DP)
@Composable
fun ImageGalleryPickTabletPreview() {
	KoinApplicationPreview {
		ImageGalleryUi(
			FakeImageGallery(
				ImageGallery.State(
					mode = ImageGalleryMode.Pick(ImagePickTarget.EntryImage(30)),
					images = previewGalleryImages(),
					loaded = true,
					selectedId = 5,
				)
			)
		)
	}
}

@Preview
@Composable
fun ImageGalleryEmptyPreview() {
	KoinApplicationPreview {
		ImageGalleryUi(
			FakeImageGallery(ImageGallery.State(mode = ImageGalleryMode.Browse, loaded = true))
		)
	}
}
