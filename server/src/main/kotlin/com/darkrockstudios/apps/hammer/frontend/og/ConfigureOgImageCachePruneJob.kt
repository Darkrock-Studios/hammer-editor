package com.darkrockstudios.apps.hammer.frontend.og

import com.darkrockstudios.apps.hammer.scheduling.launchRecurringTask
import io.ktor.server.application.Application
import io.ktor.server.application.log
import org.koin.ktor.ext.inject

/** Starts the OG-image cache prune job. Only wired when rich link previews are enabled. */
fun Application.configureOgImageCachePruneJob() {
	val job: OgImageCachePruneJob by inject()
	launchRecurringTask(job)
	log.info("OG image cache prune job started")
}
