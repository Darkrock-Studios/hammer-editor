package com.darkrockstudios.apps.hammer.monitoring

import com.darkrockstudios.apps.hammer.scheduling.launchRecurringTask
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

/**
 * Starts the monitoring maintenance job. Unlike the Patreon job, this always
 * starts: the master enable lives in the runtime [MonitoringConfig] and is
 * checked inside the loop, so toggling it does not require a restart. The loop
 * no-ops cheaply when monitoring is disabled.
 */
fun Application.configureMonitoringJob() {
	val job: MonitoringMaintenanceJob by inject()
	launchRecurringTask(job)
	log.info("Monitoring maintenance job started")
}
