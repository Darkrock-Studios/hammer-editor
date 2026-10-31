package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.projectdata.ProjectDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription

/** The project's cover on disk. [version] changes whenever the file's bytes do, so it keys image caches. */
data class CoverImage(val def: ImageDef, val path: String, val version: String)

/**
 * The project's cover, resolved again when the cover changes or its image does (replaced, renamed,
 * deleted, or downloaded by a sync after the cover id arrived). Null when there is no cover or this
 * device does not have its image. Never reads the image's bytes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun coverImageFlow(
	projectDataRepository: ProjectDataRepository,
	imagesService: ImagesService,
): Flow<CoverImage?> =
	projectDataRepository.state
		.onStart { projectDataRepository.load() }
		.filterNotNull()
		.map { it.data.coverImageId }
		.distinctUntilChanged()
		.flatMapLatest { id ->
			if (id == null) {
				flowOf(null)
			} else {
				imagesService.imageChangedFlow
					.onSubscription { emit(id) }
					.filter { it == id }
					.map { resolveCover(imagesService, id) }
			}
		}
		.distinctUntilChanged()

private suspend fun resolveCover(imagesService: ImagesService, id: Int): CoverImage? {
	imagesService.ensureImagesLoaded()
	val def = imagesService.findImage(id) ?: return null
	val version = imagesService.cacheVersion(def) ?: return null
	return CoverImage(def = def, path = imagesService.imagePath(def).path, version = version)
}
