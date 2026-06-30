package com.darkrockstudios.apps.hammer.patreon

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.scheduling.launchRecurringTask
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

fun Application.configurePatreonPolling(config: ServerConfig) {
	if (config.patreonEnabled == true) {
		val pollingJob: PatreonPollingJob by inject()
		launchRecurringTask(pollingJob)
		log.info("Patreon polling job started")
	} else {
		log.info("Patreon integration disabled at server level")
	}
}
