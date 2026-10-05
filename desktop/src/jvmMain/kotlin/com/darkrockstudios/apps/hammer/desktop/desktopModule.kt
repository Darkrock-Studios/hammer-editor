package com.darkrockstudios.apps.hammer.desktop

import com.darkrockstudios.apps.hammer.base.DistributionChannel
import com.darkrockstudios.apps.hammer.base.PackageFormat
import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdater
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.dependencyinjection.DISPATCHER_IO
import com.darkrockstudios.apps.hammer.common.getInDevelopmentMode
import com.darkrockstudios.apps.hammer.common.hostOs
import com.darkrockstudios.apps.hammer.desktop.update.GlobalSettingsUpdatePreferences
import com.darkrockstudios.apps.hammer.desktop.update.createAppUpdater
import com.darkrockstudios.apps.hammer.common.sandbox.NoopSandboxFileAccess
import com.darkrockstudios.apps.hammer.common.sandbox.SandboxFileAccess
import com.darkrockstudios.apps.hammer.desktop.sandbox.MacOsBookmarks
import com.darkrockstudios.apps.hammer.desktop.sandbox.MacOsSandboxFileAccess
import com.darkrockstudios.apps.hammer.desktop.sandbox.SandboxBookmarkStore
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
			// TODO: Disabled: the D-Bus quicklist is crashing, have to debug some day
			HostOs.Linux -> NoOpQuickShortcuts()
			HostOs.MacOs -> NoOpQuickShortcuts()
			HostOs.Other -> NoOpQuickShortcuts()
		}
	}
	single<SandboxFileAccess> {
		if (DistributionChannel.isMacAppStore && MacOsBookmarks.isAvailable) {
			MacOsSandboxFileAccess(get())
		} else {
			NoopSandboxFileAccess
		}
	}
	single<AppUpdater> {
		createAppUpdater(
			channel = DistributionChannel.current,
			format = PackageFormat.current,
			devMode = getInDevelopmentMode(),
			preferences = GlobalSettingsUpdatePreferences(get()),
			appScope = get(named(APP_SCOPE)),
			ioDispatcher = get(named(DISPATCHER_IO)),
		)
	}
}
