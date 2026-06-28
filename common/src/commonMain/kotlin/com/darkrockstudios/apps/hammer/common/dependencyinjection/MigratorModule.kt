package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.migrator.MigrateInlineAuthTokens
import com.darkrockstudios.apps.hammer.common.data.migrator.MigrateInstallIdToGlobal
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration0_1
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration1_2
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val migratorModule = module {
	factoryOf(::DataMigrator)
	factoryOf(::Migration0_1)
	factoryOf(::Migration1_2)
	factoryOf(::MigrateInstallIdToGlobal)
	factoryOf(::MigrateInlineAuthTokens)
}