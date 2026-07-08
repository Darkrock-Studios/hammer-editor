package com.darkrockstudios.apps.hammer.common

import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.dependencyinjection.aboutLibrariesModule
import com.darkrockstudios.apps.hammer.common.dependencyinjection.imageLoadingModule
import kotlinx.coroutines.runBlocking
import org.koin.mp.KoinPlatform.getKoin

fun initializeHammerApp() {
	logStartupBanner()

	initializeKoin(
		extraModules = listOf(
			imageLoadingModule,
			aboutLibrariesModule,
		)
	)

	// Run data migrations before the UI starts, matching Android (HammerApplication) and
	// desktop (Main). Without this iOS would never run global or per-project migrations.
	runBlocking { getKoin().get<DataMigrator>().handleDataMigration() }
}
