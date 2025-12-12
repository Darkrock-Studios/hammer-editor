package com.darkrockstudios.apps.hammer.common.dependencyinjection

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import org.koin.dsl.module

actual val imageLoadingModule = module {
	single<ImageLoader> {
		val context: Context = get()
		ImageLoader.Builder(context)
			.components {
				add(SvgDecoder.Factory())
			}
			.crossfade(true)
			.memoryCache {
				MemoryCache.Builder()
					.maxSizePercent(context, 0.25)
					.build()
			}
			.diskCache {
				DiskCache.Builder()
					.directory(context.cacheDir.resolve("image_cache"))
					.maxSizeBytes(512L * 1024 * 1024) // 512MB
					.build()
			}
			.build()
	}
}
