package com.darkrockstudios.apps.hammer.admin

import com.darkrockstudios.apps.hammer.scheduling.launchRecurringTask
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

/**
 * Starts the whitelist expiry job. Always runs; the pass is cheap and a no-op when
 * nothing has expired.
 */
fun Application.configureWhitelistExpiryJob() {
	val job: WhitelistExpiryJob by inject()
	launchRecurringTask(job)
	log.info("Whitelist expiry job started")
}
