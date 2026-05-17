package com.darkrockstudios.apps.hammer.desktop

import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.desktop.shortcuts.DesktopJumpListManager
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val desktopModule: Module = module {
	singleOf(::WindowGeometryStore)
	single {
		DesktopJumpListManager(
			projectsRepository = get(),
			projectMetadataDatasource = get(),
			ioDispatcher = get(named(DISPATCHER_IO)),
		)
	}
}
