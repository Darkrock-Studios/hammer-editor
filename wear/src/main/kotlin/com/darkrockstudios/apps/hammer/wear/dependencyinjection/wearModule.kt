package com.darkrockstudios.apps.hammer.wear.dependencyinjection

import androidx.work.WorkManager
import com.darkrockstudios.apps.hammer.common.data.sync.accountsync.SyncAccountUseCase
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.wear.data.ListWatchProjectsUseCase
import com.darkrockstudios.apps.hammer.wear.data.SignOutUseCase
import com.darkrockstudios.apps.hammer.wear.data.CaptureUseCase
import com.darkrockstudios.apps.hammer.wear.data.CaptureWriter
import com.darkrockstudios.apps.hammer.wear.data.RepositoryCaptureWriter
import com.darkrockstudios.apps.hammer.wear.data.RepositoryUnsyncedContentSource
import com.darkrockstudios.apps.hammer.wear.data.SubscribedProjectsRepository
import com.darkrockstudios.apps.hammer.wear.data.UnsyncedContentSource
import com.darkrockstudios.apps.hammer.wear.data.UnsyncedContentUseCase
import com.darkrockstudios.apps.hammer.wear.data.WearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.data.createWearPrefsDatasource
import com.darkrockstudios.apps.hammer.wear.pairing.DataLayerPhonePairingClient
import com.darkrockstudios.apps.hammer.wear.pairing.PhonePairingClient
import com.darkrockstudios.apps.hammer.wear.pairing.PhonePairingUseCase
import com.darkrockstudios.apps.hammer.wear.sync.AccountSync
import com.darkrockstudios.apps.hammer.wear.sync.CaptureSyncScheduler
import com.darkrockstudios.apps.hammer.wear.sync.WorkManagerCaptureSyncScheduler
import com.darkrockstudios.apps.hammer.wear.sync.DefaultSyncCoordinator
import com.darkrockstudios.apps.hammer.wear.sync.SyncCoordinator
import com.darkrockstudios.apps.hammer.wear.sync.SyncScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val wearModule: Module = module {
	single<PhonePairingClient> { DataLayerPhonePairingClient(context = androidContext()) }
	single {
		PhonePairingUseCase(
			client = get(),
			globalSettingsStore = get(),
			accountUseCase = get(),
		)
	}

	single<WearPrefsDatasource> { createWearPrefsDatasource(androidContext()) }
	single { SubscribedProjectsRepository(datasource = get()) }
	factory { ListWatchProjectsUseCase(projectsRepository = get(), subscriptions = get()) }
	single<UnsyncedContentSource> {
		RepositoryUnsyncedContentSource(
			ideasDatasource = get(),
			ideasSyncDatasource = get(),
			fileSystem = get(),
			json = get(),
		)
	}
	factory { UnsyncedContentUseCase(listProjects = get(), source = get()) }
	single<CaptureWriter> { RepositoryCaptureWriter(ideasRepository = get()) }
	single<CaptureSyncScheduler> {
		WorkManagerCaptureSyncScheduler(
			workManager = WorkManager.getInstance(androidContext()),
			globalSettingsStore = get(),
		)
	}
	factory { CaptureUseCase(writer = get(), unsyncedContent = get(), captureSync = get()) }

	single<AccountSync> {
		val syncAccountUseCase: SyncAccountUseCase = get()
		AccountSync { listener, projectFilter -> syncAccountUseCase.execute(listener, projectFilter) }
	}
	single<SyncCoordinator> {
		DefaultSyncCoordinator(
			accountSync = get(),
			subscriptions = get(),
			globalSettingsStore = get(),
			networkConnectivity = get(),
			appScope = get(named(APP_SCOPE)),
		)
	}
	single {
		SyncScheduler(
			workManager = WorkManager.getInstance(androidContext()),
			globalSettingsStore = get(),
			appScope = get(named(APP_SCOPE)),
		)
	}
	factory {
		SignOutUseCase(
			globalSettingsStore = get(),
			projectsRepository = get(),
			subscriptions = get(),
			syncScheduler = get(),
			syncCoordinator = get(),
		)
	}
}
