package com.darkrockstudios.apps.hammer.utilities

import com.darkrockstudios.apps.hammer.scheduling.launchRecurringTask
import io.ktor.server.application.Application
import io.ktor.server.application.log
import org.koin.ktor.ext.inject

/** Starts the shared disk cache prune job. */
fun Application.configureDiskCachePruneJob() {
	val job: DiskCachePruneJob by inject()
	launchRecurringTask(job)
	log.info("Disk cache prune job started")
}
