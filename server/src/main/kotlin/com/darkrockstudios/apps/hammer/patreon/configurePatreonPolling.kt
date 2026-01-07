package com.darkrockstudios.apps.hammer.patreon

import com.darkrockstudios.apps.hammer.ServerConfig
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.ktor.ext.inject

fun Application.configurePatreonPolling(config: ServerConfig) {
	if (config.patreonEnabled == true) {
		val pollingJob: PatreonPollingJob by inject()
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
		pollingJob.start(scope)
		log.info("Patreon polling job started")

		environment.monitor.subscribe(ApplicationStopped) {
			pollingJob.stop()
		}
	} else {
		log.info("Patreon integration disabled at server level")
	}
}
