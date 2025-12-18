package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.adminOnly
import com.darkrockstudios.apps.hammer.frontend.utils.withMessages
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.utils.io.*

fun Route.adminRoutes(whiteListRepository: WhiteListRepository) {
	adminOnly {
		route("/admin") {
			admin(whiteListRepository)
			whiteListRoutes(whiteListRepository)
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

private fun Route.whiteListRoutes(whiteListRepository: WhiteListRepository) {
	route("/whitelist") {
		whitelistUserFragment(whiteListRepository)
		whitelistAdd(whiteListRepository)
		whitelistRemove(whiteListRepository)
	}
}

@OptIn(ExperimentalKtorApi::class)
private fun Route.whitelistAdd(whiteListRepository: WhiteListRepository) {
	hx.post("/add") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()

		if (email.isNotEmpty()) {
			whiteListRepository.addToWhiteList(email)
		}

		// If called via HTMX, return the updated fragment. Otherwise, redirect.
		val isHtmx = call.request.headers["HX-Request"] == "true"
		if (isHtmx) {
			val model = mapOf(
				"whitelist" to whiteListRepository.getWhiteList()
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
		} else {
			// Always return to the admin page; any feedback can be added later if needed
			call.respondRedirect("/admin")
		}
	}
}

private fun Route.whitelistRemove(whiteListRepository: WhiteListRepository) {
	hx.post("/remove") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()

		if (email.isNotEmpty()) {
			whiteListRepository.removeFromWhiteList(email)
		}

		val model = mapOf(
			"whitelist" to whiteListRepository.getWhiteList()
		)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

private fun Route.whitelistUserFragment(whiteListRepository: WhiteListRepository) {
	hx.get("/user-fragment") {
		val model = mapOf(
			"whitelist" to whiteListRepository.getWhiteList()
		)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}