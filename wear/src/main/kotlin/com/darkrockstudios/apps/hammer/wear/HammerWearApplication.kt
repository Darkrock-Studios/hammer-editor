package com.darkrockstudios.apps.hammer.wear

import android.app.Application
import com.darkrockstudios.apps.hammer.common.FileLogger
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.dependencyinjection.NapierLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.appModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.installGlobalExceptionHandler
import com.darkrockstudios.apps.hammer.common.logStartupBanner
import com.darkrockstudios.apps.hammer.common.setInternalDirectories
import com.darkrockstudios.apps.hammer.wear.dependencyinjection.wearModule
import com.darkrockstudios.apps.hammer.wear.sync.SyncScheduler
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import org.koin.java.KoinJavaComponent.getKoin

class HammerWearApplication : Application() {

	private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	override fun onCreate() {
		super.onCreate()

		setInternalDirectories(this)
		Napier.base(FileLogger(scope = applicationScope))
		installGlobalExceptionHandler()
		logStartupBanner()

		startKoin {
			logger(NapierLogger())
			androidContext(this@HammerWearApplication)
			modules(
				mainModule,
				appModule(applicationScope),
				wearModule,
			)
		}

		runBlocking { getKoin().get<DataMigrator>(DataMigrator::class).handleDataMigration() }

		getKoin().get<SyncScheduler>(SyncScheduler::class).start()
	}

	override fun onTerminate() {
		super.onTerminate()
		applicationScope.cancel("Application onTerminate")
	}
}
