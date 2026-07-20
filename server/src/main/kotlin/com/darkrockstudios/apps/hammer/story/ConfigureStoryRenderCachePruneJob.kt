package com.darkrockstudios.apps.hammer.story

import com.darkrockstudios.apps.hammer.scheduling.launchRecurringTask
import io.ktor.server.application.Application
import io.ktor.server.application.log
import org.koin.ktor.ext.inject

/** Starts the story render cache prune job. */
fun Application.configureStoryRenderCachePruneJob() {
	val job: StoryRenderCachePruneJob by inject()
	launchRecurringTask(job)
	log.info("Story render cache prune job started")
}
