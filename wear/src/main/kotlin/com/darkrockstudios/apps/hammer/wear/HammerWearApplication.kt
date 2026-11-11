package com.darkrockstudios.apps.hammer.wear

import android.app.Application
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.KermitKoinLogger
import com.darkrockstudios.apps.hammer.common.dependencyinjection.appModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.common.installGlobalExceptionHandler
import com.darkrockstudios.apps.hammer.common.logStartupBanner
import com.darkrockstudios.apps.hammer.common.logging.installAndroidLogging
import com.darkrockstudios.apps.hammer.common.setInternalDirectories
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import com.darkrockstudios.apps.hammer.wear.dependencyinjection.wearModule
import com.darkrockstudios.apps.hammer.wear.sync.SyncScheduler
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
		installAndroidLogging(applicationScope)
		installGlobalExceptionHandler()
		logStartupBanner()

		startKoin {
			logger(KermitKoinLogger())
			androidContext(this@HammerWearApplication)
			modules(
				mainModule,
				appModule(applicationScope),
				wearModule,
			)
		}

		// Before migration, which can re-mint a duplicated project id.
		getKoin().get<SubscribedProjectsRepository>(SubscribedProjectsRepository::class)
			.followProjectIdChanges(getKoin().get(ProjectsRepository::class))

		runBlocking { getKoin().get<DataMigrator>(DataMigrator::class).handleDataMigration() }

		getKoin().get<SyncScheduler>(SyncScheduler::class).start()
	}

	override fun onTerminate() {
		super.onTerminate()
		applicationScope.cancel("Application onTerminate")
	}
}
