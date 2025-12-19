package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.homeRoutes(config: ServerConfig, whiteListRepository: WhiteListRepository) {
	route("/") {
		get {
			val model = call.withDefaults()
			val useWhiteList = whiteListRepository.useWhiteList()
			model["serverMessage"] = config.serverMessage

			if (useWhiteList && config.contact.isNullOrBlank().not()) {
				model["whitelistEnabled"] = useWhiteList
				call.msg(model, "home_servermessage_whitelist", config.contact)
			}
			call.respond(MustacheContent("home.mustache", model))
		}
	}
}