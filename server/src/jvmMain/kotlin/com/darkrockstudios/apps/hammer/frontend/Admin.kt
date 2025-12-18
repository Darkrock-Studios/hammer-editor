package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.adminOnly
import com.darkrockstudios.apps.hammer.frontend.utils.withMessages
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.adminRoutes(whiteListRepository: WhiteListRepository) {
	adminOnly {
		route("/admin") {
			admin(whiteListRepository)
			whitelistAdd(whiteListRepository)
			whitelistRemove(whiteListRepository)
		}
	}
}

private fun Route.admin(whiteListRepository: WhiteListRepository) {
	get {
		val session = call.sessions.get<UserSession>()
		val model = mapOf(
			"isAdmin" to (session?.isAdmin?.toString() ?: "null"),
			// List of all whitelist users for the HMX admin page
			"whitelist" to whiteListRepository.getWhiteList()
		)
		call.respond(MustacheContent("admin.mustache", call.withMessages(model)))
	}
}

private fun Route.whitelistAdd(whiteListRepository: WhiteListRepository) {
	post("/whitelist/add") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()

		if (email.isNotEmpty()) {
			whiteListRepository.addToWhiteList(email)
		}

		// Always return to the admin page; any feedback can be added later if needed
		call.respondRedirect("/admin")
	}
}

private fun Route.whitelistRemove(whiteListRepository: WhiteListRepository) {
	post("/whitelist/remove") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()

		if (email.isNotEmpty()) {
			whiteListRepository.removeFromWhiteList(email)
		}

		// Return to the admin page after processing
		call.respondRedirect("/admin")
	}
}