package com.darkrockstudios.apps.hammer.account

import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.ktor.ext.inject

/**
 * Starts the token maintenance job. Always runs; the cleanup is cheap and a
 * no-op when there is nothing expired to delete.
 */
fun Application.configureTokenMaintenanceJob() {
	val job: TokenMaintenanceJob by inject()
	val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	job.start(scope)
	log.info("Token maintenance job started")

	environment.monitor.subscribe(ApplicationStopped) {
		job.stop()
	}
}
