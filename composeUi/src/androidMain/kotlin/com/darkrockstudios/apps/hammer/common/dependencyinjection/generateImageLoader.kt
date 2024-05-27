package com.darkrockstudios.apps.hammer.common.dependencyinjection

import android.content.Context
import com.darkrockstudios.apps.hammer.common.compose.ImageLoaderNapierLogger
import com.eygraber.uri.Uri
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.cache.memory.maxSizePercent
import com.seiko.imageloader.component.mapper.Mapper
import com.seiko.imageloader.component.setupDefaultComponents
import com.seiko.imageloader.intercept.bitmapMemoryCacheConfig
import com.seiko.imageloader.intercept.imageMemoryCacheConfig
import com.seiko.imageloader.intercept.painterMemoryCacheConfig
import com.seiko.imageloader.option.Options
import com.seiko.imageloader.util.LogPriority
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath

internal fun generateImageLoader(context: Context, fileSystem: FileSystem): ImageLoader {
	return ImageLoader {
		components {
			setupDefaultComponents(context)
			add(LocalFileUriMapper())
		}
		logger = ImageLoaderNapierLogger(LogPriority.WARN)
		interceptor {
			// cache 25% memory bitmap
			bitmapMemoryCacheConfig {
				maxSizePercent(context, 0.25)
			}
			// cache 50 image
			imageMemoryCacheConfig {
				maxSize(50)
			}
			// cache 50 painter
			painterMemoryCacheConfig {
				maxSize(50)
			}
			diskCacheConfig {
				directory(context.cacheDir.resolve("image_cache").toOkioPath())
				maxSizeBytes(512L * 1024 * 1024) // 512MB
			}
		}
	}
}

private class LocalFileUriMapper : Mapper<okio.Path> {
	override fun map(data: Any, options: Options): okio.Path? {
		if (data !is Uri) return null
		if (!isApplicable(data)) return null
		return data.toString().toPath()
	}

	private fun isApplicable(data: Uri): Boolean {
		return data.scheme.isNullOrBlank()
	}
}