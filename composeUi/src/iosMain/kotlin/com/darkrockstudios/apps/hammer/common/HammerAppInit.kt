package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.common.dependencyinjection.aboutLibrariesModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.imageLoadingModule

fun initializeHammerApp() {
	initializeKoin(
		extraModules = listOf(
			imageLoadingModule,
			aboutLibrariesModule,
		)
	)
}
