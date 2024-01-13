package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.DesktopSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.util.DeviceLocaleResolver
import com.darkrockstudios.apps.hammer.common.util.NetworkConnectivity
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.common.util.StrResImpl
import com.darkrockstudios.apps.hammer.common.util.UrlLauncher
import com.darkrockstudios.apps.hammer.common.util.UrlLauncherDesktop
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule = module {
	singleOf(::NetworkConnectivity)
	singleOf(::StrResImpl) bind StrRes::class
	singleOf(::DeviceLocaleResolver)
	singleOf(::UrlLauncherDesktop) bind UrlLauncher::class
	factory { params -> DesktopSettingsComponent(componentContext = params.get()) } bind PlatformSettings::class
}