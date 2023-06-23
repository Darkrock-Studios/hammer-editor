package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.seiko.imageloader.ImageLoader
import org.koin.dsl.module

actual val imageLoadingModule = module {
	single<ImageLoader> { generateImageLoader(get()) }
}