package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.util.LibraryInfoProvider
import com.mikepenz.aboutlibraries.Libs
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

private class IosLibraryInfoProvider : LibraryInfoProvider {
	override fun getLibs(): Libs = Libs.Builder().build()
}

val aboutLibrariesModule: Module = module {
	singleOf(::IosLibraryInfoProvider) bind LibraryInfoProvider::class
}
