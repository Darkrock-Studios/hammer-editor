package com.darkrockstudios.apps.hammer.plugins

import com.darkrockstudios.apps.hammer.base.http.createNetworkJsonSerializer
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

fun Application.configureSerialization() {
	install(ContentNegotiation) {
		json(createNetworkJsonSerializer())
	}
}
