package com.darkrockstudios.apps.hammer.services.blogfeed

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import com.darkrockstudios.apps.hammer.scheduling.launchRecurringTask
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.util.logging.Logger
import org.koin.ktor.ext.inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class BlogFeedRefreshJob(
	private val repository: BlogFeedRepository,
	logger: Logger,
) : RecurringTask("Blog feed refresh job", logger) {

	/** The blog being unreachable is not this server failing. */
	override val affectsHealth = false

	override suspend fun tick() = repository.refresh()

	override suspend fun nextDelay(): Duration = REFRESH_INTERVAL

	override suspend fun errorBackoff(): Duration = RETRY_INTERVAL

	private companion object {
		val REFRESH_INTERVAL = 1.hours
		val RETRY_INTERVAL = 10.minutes
	}
}

/** Starts the refresh job, unless the blog feed is switched off. */
fun Application.configureBlogFeedJob() {
	val serverConfig: ServerConfig by inject()
	if (!serverConfig.home.blogFeed) return
	val job: BlogFeedRefreshJob by inject()
	launchRecurringTask(job)
	log.info("Blog feed refresh job started")
}
