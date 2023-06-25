package com.darkrockstudios.apps.hammer.common.dependencyinjection

import android.content.Context
import com.darkrockstudios.apps.hammer.common.compose.ImageLoaderNapierLogger
import com.darkrockstudios.apps.hammer.common.getImageCacheDirectory
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.cache.disk.DiskCache
import com.seiko.imageloader.cache.memory.MemoryCache
import com.seiko.imageloader.cache.memory.maxSizePercent
import com.seiko.imageloader.component.setupDefaultComponents
import com.seiko.imageloader.util.LogPriority
import okio.FileSystem
import okio.Path.Companion.toPath

internal fun generateImageLoader(context: Context, fileSystem: FileSystem): ImageLoader {
	return ImageLoader {
		components {
			setupDefaultComponents(context)
		}
		logger = ImageLoaderNapierLogger(LogPriority.WARN)
		interceptor {
			memoryCache {
				MemoryCache {
					// Set the max size to 25% of the app's available memory.
					maxSizePercent(context, 0.25)
				}
			}
			diskCache {
				DiskCache(fileSystem) {
					directory(getImageCacheDirectory().toPath())
					maxSizeBytes(128L * 1024 * 1024)
				}
			}
		}
	}
}