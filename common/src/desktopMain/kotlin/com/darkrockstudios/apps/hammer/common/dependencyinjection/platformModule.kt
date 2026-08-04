package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.DesktopPlatformSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.components.storyeditor.focusmode.FocusModeService
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsDatasource
import com.darkrockstudios.apps.hammer.common.data.globalsettings.datasource.GlobalSettingsFilesystemDatasource
import com.darkrockstudios.apps.hammer.common.data.projectbackup.BackupManagerService
import com.darkrockstudios.apps.hammer.common.spellcheck.LanguageUtil
import com.darkrockstudios.apps.hammer.common.util.*
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.factory

actual val platformModule = module {
	single<NetworkConnectivity>()
	single<StrResImpl>() bind StrRes::class
	single<DeviceLocaleResolver>()
	single<AvailableLocalesProvider>()
	single<UrlLauncherDesktop>() bind UrlLauncher::class
	single<LanguageUtil>()
	factory { params -> DesktopPlatformSettingsComponent(componentContext = params.get()) } bind PlatformSettings::class
	single<PlatformSpellCheckerFactory>()
	factory<FocusModeService>()
	single<BackupManagerService>()
	single<GlobalSettingsDatasource> { get<GlobalSettingsFilesystemDatasource>() }
}