package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.migrator.MigrateInlineAuthTokens
import com.darkrockstudios.apps.hammer.common.data.migrator.MigrateInstallIdToGlobal
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration0_1
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration1_2
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val migratorModule = module {
	factory<DataMigrator>()
	factory<Migration0_1>()
	factory<Migration1_2>()
	factory<MigrateInstallIdToGlobal>()
	factory<MigrateInlineAuthTokens>()
}