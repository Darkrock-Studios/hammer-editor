package com.darkrockstudios.apps.hammer.services.blogfeed

import com.darkrockstudios.apps.hammer.scheduling.RecurringTask
import com.darkrockstudios.apps.hammer.scheduling.launchRecurringTask
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.util.logging.Logger
import kotlinx.coroutines.CancellationException
import org.koin.ktor.ext.inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class BlogFeedRefreshJob(
	private val repository: BlogFeedRepository,
	logger: Logger,
) : RecurringTask("Blog feed refresh job", logger) {

	private var lastRefreshFailed = false

	/** Another site being unreachable is not this server failing, so it never reaches the health report. */
	override suspend fun tick() {
		lastRefreshFailed = try {
			repository.refresh()
			false
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			logger.warn("Blog feed refresh failed: ${e::class.simpleName}: ${e.message}")
			true
		}
	}

	override suspend fun nextDelay(): Duration = if (lastRefreshFailed) RETRY_INTERVAL else REFRESH_INTERVAL

	private companion object {
		val REFRESH_INTERVAL = 1.hours
		val RETRY_INTERVAL = 10.minutes
	}
}

/** Starts the refresh job, unless no blog feed is configured. */
fun Application.configureBlogFeedJob() {
	val repository: BlogFeedRepository by inject()
	if (!repository.isConfigured) return
	val job: BlogFeedRefreshJob by inject()
	launchRecurringTask(job)
	log.info("Blog feed refresh job started")
}
