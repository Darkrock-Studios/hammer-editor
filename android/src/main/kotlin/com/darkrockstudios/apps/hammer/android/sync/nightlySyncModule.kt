package com.darkrockstudios.apps.hammer.android.sync

import androidx.work.WorkManager
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val nightlySyncModule: Module = module {
	single {
		NightlySyncScheduler(
			workManager = WorkManager.getInstance(androidContext()),
			globalSettingsStore = get(),
			appScope = get(named(APP_SCOPE)),
			clock = get(),
		)
	}
}
