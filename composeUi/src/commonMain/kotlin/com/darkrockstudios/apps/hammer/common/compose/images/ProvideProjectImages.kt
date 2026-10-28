package com.darkrockstudios.apps.hammer.common.compose.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.darkrockstudios.apps.hammer.common.compose.LocalImageProvider
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.ImagesService
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope

/** Gives [content] one [HammerImageProvider] for [projectDef], closed when it leaves composition. */
@Composable
fun ProvideProjectImages(projectDef: ProjectDef, content: @Composable () -> Unit) {
	val context = LocalPlatformContext.current
	val imageLoader = SingletonImageLoader.get(context)
	val provider = remember(projectDef, imageLoader, context) {
		HammerImageProvider(
			imagesService = ProjectDefScope(projectDef).scope.get<ImagesService>(),
			bitmapLoader = CoilImageBitmapLoader(imageLoader, context),
		)
	}
	DisposableEffect(provider) {
		onDispose { provider.close() }
	}
	CompositionLocalProvider(LocalImageProvider provides provider, content = content)
}
