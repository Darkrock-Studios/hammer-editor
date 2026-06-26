package com.darkrockstudios.apps.hammer.monitoring

import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.inject

/**
 * Starts the monitoring maintenance job. Unlike the Patreon job, this always
 * starts: the master enable lives in the runtime [MonitoringConfig] and is
 * checked inside the loop, so toggling it does not require a restart. The loop
 * no-ops cheaply when monitoring is disabled.
 */
fun Application.configureMonitoringJob() {
	val job: MonitoringMaintenanceJob by inject()
	val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	job.start(scope)
	log.info("Monitoring maintenance job started")

	// Block until the loop's in-flight tick finishes so a flush or maintenance
	// pass can't outlive the application and mutate state after shutdown.
	environment.monitor.subscribe(ApplicationStopped) {
		runBlocking { job.stopAndJoin() }
		scope.cancel()
	}
}
