package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.compose.NullDataInterceptor
import com.darkrockstudios.apps.hammer.common.compose.commonConfig
import com.darkrockstudios.apps.hammer.common.getImageCacheDirectory
import com.eygraber.uri.Uri
import com.seiko.imageloader.ImageLoader
import com.seiko.imageloader.ImageLoaderBuilder
import com.seiko.imageloader.cache.disk.DiskCacheBuilder
import com.seiko.imageloader.cache.memory.MemoryCacheBuilder
import com.seiko.imageloader.component.decoder.SkiaImageDecoder
import com.seiko.imageloader.component.mapper.Mapper
import com.seiko.imageloader.request.Options
import io.github.aakira.napier.Napier
import okio.Path.Companion.toPath
import java.io.File

internal fun generateImageLoader(): ImageLoader {
	return ImageLoaderBuilder()
		.commonConfig()
		.memoryCache {
			MemoryCacheBuilder()
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
		.components {
			add(WindowsFileUriMapper())
			add(SkiaImageDecoder.Factory())
		}
		.build()
}

private class WindowsFileUriMapper : Mapper<File> {
	override fun map(data: Any, options: Options): File? {
		if (data !is Uri) return null
		if (!isApplicable(data)) return null

		return File(data.toString()).also {
			Napier.v(
				tag = "WindowsFileUriMapper",
			) { "mapper to File" }
		}
	}

	private val pattern = Regex("""^[a-zA-Z]$""")

	private fun isApplicable(data: Uri): Boolean {
		return pattern.matches(data.scheme ?: "")
	}
}
