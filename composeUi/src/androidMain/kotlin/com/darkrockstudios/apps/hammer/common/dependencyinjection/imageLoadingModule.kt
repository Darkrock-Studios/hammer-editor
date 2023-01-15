package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.seiko.imageloader.ImageLoader
import org.koin.core.module.Module
import org.koin.dsl.module

actual val imageLoadingModule: Module = module {
	single<ImageLoader> { generateImageLoader(get()) }
}