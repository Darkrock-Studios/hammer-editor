package com.darkrockstudios.apps.hammer.desktop

import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.hostOs
import com.darkrockstudios.apps.hammer.desktop.shortcuts.*
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

val desktopModule: Module = module {
	singleOf(::WindowGeometryStore)
	single<QuickShortcuts> {
		val projects = get<ProjectsRepository>()
		val ioDispatcher = get<CoroutineContext>(named(DISPATCHER_IO))
		when (hostOs) {
			HostOs.Windows -> WindowsJumpList(projects, ioDispatcher)
			HostOs.Linux -> LinuxQuicklist(projects, ioDispatcher)
			HostOs.MacOs -> NoOpQuickShortcuts()
			HostOs.Other -> NoOpQuickShortcuts()
		}
	}
}
