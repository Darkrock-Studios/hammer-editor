package com.darkrockstudios.apps.hammer.desktop

import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.IS_APP_STORE
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.hostOs
import com.darkrockstudios.apps.hammer.common.sandbox.NoopSandboxFileAccess
import com.darkrockstudios.apps.hammer.common.sandbox.SandboxFileAccess
import com.darkrockstudios.apps.hammer.desktop.sandbox.MacOsBookmarks
import com.darkrockstudios.apps.hammer.desktop.sandbox.MacOsSandboxFileAccess
import com.darkrockstudios.apps.hammer.desktop.sandbox.SandboxBookmarkStore
import com.darkrockstudios.apps.hammer.desktop.shortcuts.LinuxQuicklist
import com.darkrockstudios.apps.hammer.desktop.shortcuts.NoOpQuickShortcuts
import com.darkrockstudios.apps.hammer.desktop.shortcuts.QuickShortcuts
import com.darkrockstudios.apps.hammer.desktop.shortcuts.WindowsJumpList
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

val desktopModule: Module = module {
	singleOf(::WindowGeometryStore)
	singleOf(::SandboxBookmarkStore)
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
	single<SandboxFileAccess> {
		if (IS_APP_STORE && MacOsBookmarks.isAvailable) {
			MacOsSandboxFileAccess(get())
		} else {
			NoopSandboxFileAccess
		}
	}
}
