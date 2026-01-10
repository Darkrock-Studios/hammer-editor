package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.setupPage(serverConfig: ServerConfig) {
	val accountsRepository: AccountsRepository by inject()

	route("/setup") {
		get {
			// If users already exist, redirect to home
			if (accountsRepository.hasUsers()) {
				call.respondRedirect("/")
				return@get
			}

			val model = call.withDefaults()
			model["page_stylesheet"] = "/assets/css/error.css"

			// Build the server address for display
			val serverAddress = buildServerAddress(serverConfig)
			model["serverAddress"] = serverAddress

			call.respond(MustacheContent("setup.mustache", model))
		}
	}
}

private fun buildServerAddress(config: ServerConfig): String {
	val host = config.host
	val port = config.port
	val sslPort = config.sslPort
	val hasSsl = config.sslCert != null

	return if (hasSsl) {
		if (sslPort == 443) {
			"https://$host"
		} else {
			"https://$host:$sslPort"
		}
	} else {
		if (port == 80) {
			"http://$host"
		} else {
			"http://$host:$port"
		}
	}
}
