package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.scheduling.launchRecurringTask
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

/**
 * Starts the token maintenance job. Always runs; the cleanup is cheap and a
 * no-op when there is nothing expired to delete.
 */
fun Application.configureTokenMaintenanceJob() {
	val job: TokenMaintenanceJob by inject()
	launchRecurringTask(job)
	log.info("Token maintenance job started")
}
