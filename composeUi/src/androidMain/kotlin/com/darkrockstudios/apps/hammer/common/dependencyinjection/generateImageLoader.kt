package com.darkrockstudios.apps.hammer.common.dependencyinjection

import android.content.Context
import com.darkrockstudios.apps.hammer.common.compose.NullDataInterceptor
import com.darkrockstudios.apps.hammer.common.compose.commonConfig
import com.darkrockstudios.apps.hammer.common.getImageCacheDirectory
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.ImageLoaderBuilder
import com.seiko.imageloader.cache.disk.DiskCacheBuilder
import com.seiko.imageloader.cache.memory.MemoryCacheBuilder
import okio.Path.Companion.toPath

internal fun generateImageLoader(context: Context): ImageLoader {
	return ImageLoaderBuilder(context)
		.commonConfig()
		.memoryCache {
			MemoryCacheBuilder(context)
				// Set the max size to 25% of the app's available memory.
				.maxSizePercent(0.25)
				.build()
		}
		.diskCache {
			DiskCacheBuilder()
				.directory(getImageCacheDirectory().toPath())
				.maxSizeBytes(128L * 1024 * 1024) // 128MB
				.build()
		}
		.addInterceptor(NullDataInterceptor)
		.build()
}