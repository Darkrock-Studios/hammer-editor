package com.darkrockstudios.apps.hammer.common.dependencyinjection

import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.migrator.Migration0_1
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val migratorModule = module {
	factoryOf(::DataMigrator)
	factoryOf(::Migration0_1)
}