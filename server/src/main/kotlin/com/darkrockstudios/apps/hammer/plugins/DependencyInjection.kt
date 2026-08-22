package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.dependencyinjection.mainModule
import com.darkrockstudios.apps.hammer.plugin.PluginRegistry
import com.darkrockstudios.apps.hammer.plugin.ServerPlugin
import com.darkrockstudios.apps.hammer.plugin.installedPlugins
import io.ktor.server.application.*
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDependencyInjection(
	serverConfig: ServerConfig = ServerConfig(),
	addInModule: Module? = null,
	plugins: List<ServerPlugin> = installedPlugins(),
) {
	val logger = log

	val pluginRegistry = PluginRegistry.create(plugins, logger)

	val serverConfigModule = module {
		single { serverConfig }
		single { pluginRegistry }
	}

	install(Koin) {
		slf4jLogger()

		val modules = mutableListOf(mainModule(logger), serverConfigModule)
		pluginRegistry.plugins.mapNotNullTo(modules) { it.koinModule() }
		if (addInModule != null) {
			modules.add(addInModule)
		}
		modules(modules)
	}

	val db: Database = get()
	db.initialize()

	environment.monitor.subscribe(ApplicationStopped) {
		db.close()
	}
}