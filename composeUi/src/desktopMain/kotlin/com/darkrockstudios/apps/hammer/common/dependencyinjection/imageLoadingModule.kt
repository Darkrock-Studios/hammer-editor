package com.darkrockstudios.apps.hammer.common.dependencyinjection

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.darkrockstudios.apps.hammer.common.getImageCacheDirectory
import okio.Path.Companion.toPath
import org.koin.dsl.module

actual val imageLoadingModule = module {
	single<ImageLoader> {
		ImageLoader.Builder(PlatformContext.INSTANCE)
			.components {
				add(SvgDecoder.Factory())
			}
			.crossfade(true)
			.memoryCache {
				MemoryCache.Builder()
					.maxSizeBytes(32 * 1024 * 1024) // 32MB
					.build()
			}
			.diskCache {
				DiskCache.Builder()
					.directory(getImageCacheDirectory().toPath())
					.maxSizeBytes(512L * 1024 * 1024) // 512MB
					.build()
			}
			.build()
	}
}
