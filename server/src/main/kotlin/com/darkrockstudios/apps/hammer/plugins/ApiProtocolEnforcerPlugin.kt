package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.base.http.API_ROUTE_PREFIX
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_HEADER
import com.darkrockstudios.apps.hammer.base.http.HAMMER_PROTOCOL_VERSION
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallSetup
import io.ktor.server.request.path


val ApiProtocolEnforcerPlugin = createApplicationPlugin("ProtocolEnforcerPlugin") {
	on(CallSetup) { call ->
		val firstPathSegment = call.request.path().trim('/').split("/").firstOrNull()
		if (firstPathSegment == API_ROUTE_PREFIX) {
			val clientProtocolVersion = call.request.headers[HAMMER_PROTOCOL_HEADER]?.toIntOrNull()
			if (clientProtocolVersion != HAMMER_PROTOCOL_VERSION) {
				throw UnsupportedProtocolVersionException(
					clientProtocolVersion,
					HAMMER_PROTOCOL_VERSION
				)
			}
		}
	}
}