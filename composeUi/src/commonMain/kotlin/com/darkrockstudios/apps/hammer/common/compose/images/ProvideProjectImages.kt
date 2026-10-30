package com.darkrockstudios.apps.hammer.common.compose.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.darkrockstudios.apps.hammer.common.compose.LocalImageNames
import com.darkrockstudios.apps.hammer.common.compose.LocalImageProvider
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope

/**
 * Gives [content] one [HammerImageProvider] for [projectDef], closed when it leaves composition, and
 * the names of [projectDef]'s images as [LocalImageNames].
 */
@Composable
fun ProvideProjectImages(projectDef: ProjectDef, content: @Composable () -> Unit) {
	val context = LocalPlatformContext.current
	val imageLoader = SingletonImageLoader.get(context)
	val imagesService = remember(projectDef) { ProjectDefScope(projectDef).scope.get<ImagesService>() }
	val provider = remember(imagesService, imageLoader, context) {
		HammerImageProvider(
			imagesService = imagesService,
			bitmapLoader = CoilImageBitmapLoader(imageLoader, context),
		)
	}
	DisposableEffect(provider) {
		onDispose { provider.close() }
	}
	val images by imagesService.imagesFlow.collectAsState(
		imagesService.imagesFlow.replayCache.firstOrNull() ?: emptyList()
	)
	val imageNames: (Int) -> String? = remember(images) { images.associate { it.id to it.name }::get }
	CompositionLocalProvider(
		LocalImageProvider provides provider,
		LocalImageNames provides imageNames,
		content = content,
	)
}
