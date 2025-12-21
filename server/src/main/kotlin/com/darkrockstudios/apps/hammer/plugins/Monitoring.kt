package com.darkrockstudios.apps.hammer.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import org.slf4j.event.Level

fun Application.configureMonitoring(passedLogLevel: Level? = null) {
	val logLevel = passedLogLevel ?: Level.INFO

	install(CallLogging) {
		level = logLevel
		filter { call -> call.request.path().startsWith("/") }
	}
	log.info("Monitoring enabled - Level Level: $logLevel")
}
