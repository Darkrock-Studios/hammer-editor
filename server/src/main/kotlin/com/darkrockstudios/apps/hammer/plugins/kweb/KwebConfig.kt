package com.darkrockstudios.apps.hammer.plugins.kweb

import com.darkrockstudios.apps.hammer.utilities.getFileFromResource
import com.darkrockstudios.apps.hammer.utilities.getFileFromResourceAsStream
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kweb.config.KwebConfiguration
import java.time.Duration

val hammerKwebConfig = object : KwebConfiguration() {
	override val buildpageTimeout = Duration.ofSeconds(5)
	override val clientStateStatsEnabled = false
	override val clientStateTimeout = Duration.ofSeconds(5)

	override suspend fun faviconIco(call: ApplicationCall) {
		val bytes = getFileFromResourceAsStream("/assets/favicon.ico").readAllBytes()
		call.respondBytes(
			contentType = ContentType(contentType = "x", contentSubtype = "icon"),
			provider = { bytes }
		)
	}

	override suspend fun robotsTxt(call: ApplicationCall) {
		call.respondFile(getFileFromResource("assets/robots.txt"))
	}
}