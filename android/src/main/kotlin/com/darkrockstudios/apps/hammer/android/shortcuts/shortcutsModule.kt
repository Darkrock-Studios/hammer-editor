package com.darkrockstudios.apps.hammer.android.shortcuts

import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val shortcutsModule: Module = module {
	single {
		ProjectShortcutsManager(
			context = get(),
			projectsRepository = get(),
			projectMetadataDatasource = get(),
			ioDispatcher = get(named(DISPATCHER_IO)),
		)
	}
}
