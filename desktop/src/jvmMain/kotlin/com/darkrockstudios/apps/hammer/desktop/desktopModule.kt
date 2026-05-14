package com.darkrockstudios.apps.hammer.desktop

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val desktopModule: Module = module {
	singleOf(::WindowGeometryStore)
}
