package com.darkrockstudios.apps.hammer.common.preview

import coil3.ImageLoader
import coil3.PlatformContext

/**
 * Minimal coil3 [ImageLoader] so previews of screens that render `AsyncImage`
 * (encyclopedia entries, cover art) can resolve it from the preview Koin graph.
 */
fun previewImageLoader(): ImageLoader = ImageLoader.Builder(PlatformContext.INSTANCE).build()
