package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.database.Database
import com.darkrockstudios.apps.hammer.dependencyinjection.mainModule
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDependencyInjection(fileSystem: FileSystem, test: Boolean) {
	val logger = log

	install(Koin) {
		slf4jLogger()

		modules(mainModule(logger, fileSystem, test))
	}

	val db: Database = get()
	db.initialize()

	environment.monitor.subscribe(ApplicationStopped) {
		db.close()
	}

	runBlocking {
		val whiteListRepository: WhiteListRepository = get()
		whiteListRepository.initialize()
	}
}