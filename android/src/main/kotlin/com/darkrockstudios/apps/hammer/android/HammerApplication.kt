package com.darkrockstudios.apps.hammer.android

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.darkrockstudios.apps.hammer.android.aboutlibraries.aboutLibrariesModule
import com.darkrockstudios.apps.hammer.android.shortcuts.ProjectShortcutsManager
import com.darkrockstudios.apps.hammer.android.shortcuts.shortcutsModule
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.dependencyinjection.NapierLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.appModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.imageLoadingModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.setExternalDirectories
import com.darkrockstudios.apps.hammer.common.setInternalDirectories
import com.darkrockstudios.apps.hammer.common.util.AndroidSettingsKeys
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.java.KoinJavaComponent
import org.koin.java.KoinJavaComponent.getKoin

class HammerApplication : Application(), SingletonImageLoader.Factory {

	private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	override fun onCreate() {
		super.onCreate()

		initializeDirectories()
		Napier.base(FileLogger(scope = applicationScope))

		startKoin {
			logger(NapierLogger())
			androidContext(this@HammerApplication)
			modules(
				mainModule,
				imageLoadingModule,
				aboutLibrariesModule,
				shortcutsModule,
				appModule(applicationScope)
			)
		}

		runBlocking { getKoin().get<DataMigrator>(DataMigrator::class).handleDataMigration() }

		applicationScope.launch {
			getKoin().get<ProjectShortcutsManager>().refresh()
		}
	}

	override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
		return KoinJavaComponent.getKoin().get()
	}

	private fun initializeDirectories() {
		val useInternalData = Settings().getBoolean(
			AndroidSettingsKeys.KEY_USE_INTERNAL_STORAGE,
			true
		)
		if (useInternalData) {
			setInternalDirectories(this)
		} else {
			setExternalDirectories(this)
		}
	}

	override fun onTerminate() {
		super.onTerminate()
		applicationScope.cancel("Application onTerminate")
	}
}
