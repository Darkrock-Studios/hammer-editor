package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.health.isHealthPath
import io.ktor.http.isSuccess
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import org.slf4j.event.Level

fun Application.configureMonitoring(passedLogLevel: Level? = null) {
	val logLevel = passedLogLevel ?: Level.INFO

	install(CallLogging) {
		level = logLevel
		// Healthy probes would flood the log; failing ones and rejected tokens are worth seeing.
		filter { call ->
			val path = call.request.path()
			path.startsWith("/") && !(isHealthPath(path) && call.response.status()?.isSuccess() == true)
		}
	}
	log.info("Monitoring enabled - Level Level: $logLevel")
}
