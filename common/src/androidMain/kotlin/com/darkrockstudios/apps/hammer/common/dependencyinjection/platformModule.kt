package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.AndroidPlatformSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings.PlatformSettings
import com.darkrockstudios.apps.hammer.common.spellcheck.LanguageUtil
import com.darkrockstudios.apps.hammer.common.util.*
import com.darkrockstudios.libs.platformspellchecker.PlatformSpellCheckerFactory
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule = module {
	singleOf(::NetworkConnectivity)
	singleOf(::StrResImpl) bind StrRes::class
	singleOf(::DeviceLocaleResolver)
	singleOf(::UrlLauncherAndroid) bind UrlLauncher::class
	singleOf(::LanguageUtil)
	factory { params ->
		AndroidPlatformSettingsComponent(
			componentContext = params.get(),
			context = get(),
			fileSystem = get(),
		)
	} bind PlatformSettings::class
	singleOf(::PlatformSpellCheckerFactory)
}