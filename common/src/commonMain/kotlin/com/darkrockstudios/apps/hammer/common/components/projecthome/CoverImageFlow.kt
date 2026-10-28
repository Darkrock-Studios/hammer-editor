package com.darkrockstudios.apps.hammer.common.components.projecthome

import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageFile
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

/**
 * The project's cover file, resolved again when the cover changes or its image does (replaced,
 * renamed, deleted, or downloaded by a sync after the cover id arrived). Null when there is no
 * cover or this device does not have its image.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun coverImageFlow(
	projectDataRepository: ProjectDataRepository,
	imagesService: ImagesService,
): Flow<ImageFile?> =
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
					.map { imagesService.loadImageFile(id) }
			}
		}
		.distinctUntilChanged()
