package com.darkrockstudios.apps.hammer.android.aboutlibraries

import com.darkrockstudios.apps.hammer.common.util.LibraryInfoProvider
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module


val aboutLibrariesModule: Module = module {
	singleOf(::AndroidLibraryInfoProvider) bind LibraryInfoProvider::class
}