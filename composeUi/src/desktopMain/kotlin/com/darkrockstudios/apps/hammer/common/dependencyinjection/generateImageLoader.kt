package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.compose.ImageLoaderNapierLogger
import com.darkrockstudios.apps.hammer.common.compose.NullDataInterceptor
import com.darkrockstudios.apps.hammer.common.getImageCacheDirectory
import com.eygraber.uri.Uri
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.component.decoder.SkiaImageDecoder
import com.seiko.imageloader.component.mapper.Mapper
import com.seiko.imageloader.component.setupDefaultComponents
import com.seiko.imageloader.defaultImageResultMemoryCache
import com.seiko.imageloader.option.Options
import com.seiko.imageloader.util.LogPriority
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File

internal fun generateImageLoader(fileSystem: FileSystem): ImageLoader {
	return ImageLoader {
		components {
			setupDefaultComponents()
			add(WindowsFileUriMapper())
			add(SkiaImageDecoder.Factory())
		}
		logger = ImageLoaderNapierLogger(LogPriority.WARN)
		interceptor {
			addInterceptor(NullDataInterceptor)
			// cache 100 success image result, without bitmap
			defaultImageResultMemoryCache()
			memoryCacheConfig {
				maxSizeBytes(32 * 1024 * 1024) // 32MB
			}
			diskCacheConfig {
				directory(getImageCacheDirectory().toPath())
				maxSizeBytes(512L * 1024 * 1024) // 512MB
			}
		}
	}
}

private class WindowsFileUriMapper : Mapper<File> {
	override fun map(data: Any, options: Options): File? {
		if (data !is Uri) return null
		if (!isApplicable(data)) return null
		return File(data.toString())
	}

	private val pattern = Regex("""^[a-zA-Z]$""")

	private fun isApplicable(data: Uri): Boolean {
		return pattern.matches(data.scheme ?: "")
	}
}