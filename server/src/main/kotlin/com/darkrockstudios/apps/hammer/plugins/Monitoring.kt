package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.health.isHealthPath
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import org.slf4j.event.Level

fun Application.configureMonitoring(passedLogLevel: Level? = null) {
	val logLevel = passedLogLevel ?: Level.INFO

	install(CallLogging) {
		level = logLevel
		// Probes repeat every few seconds; the health code logs its own failures, throttled.
		filter { call -> call.request.path().let { it.startsWith("/") && !isHealthPath(it) } }
	}
	log.info("Monitoring enabled - Level Level: $logLevel")
}
