package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.adminOnly
import io.ktor.server.application.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.utils.io.*
import kotlin.math.ceil

fun Route.adminRoutes(config: ServerConfig, whiteListRepository: WhiteListRepository) {
	adminOnly {
		route("/admin") {
			admin(config, whiteListRepository)
			whiteListRoutes(whiteListRepository)
		}
	}
}

private fun Route.admin(config: ServerConfig, whiteListRepository: WhiteListRepository) {
	get {
		val session = call.sessions.get<UserSession>()
		val model = mapOf(
			"isAdmin" to (session?.isAdmin?.toString() ?: "null"),
			"whitelist" to mapOf(
				"enabled" to whiteListRepository.useWhiteList()
			),
			"contactEmail" to (config.contact ?: ""),
			"serverMessage" to config.serverMessage
		)
		call.respond(MustacheContent("admin.mustache", call.withDefaults(model)))
	}
}

private fun Route.whiteListRoutes(whiteListRepository: WhiteListRepository) {
	route("/whitelist") {
		whitelistUserFragment(whiteListRepository)
		whitelistAdd(whiteListRepository)
		whitelistRemove(whiteListRepository)
		whitelistSettings(whiteListRepository)
	}
}

@OptIn(ExperimentalKtorApi::class)
private fun Route.whitelistAdd(whiteListRepository: WhiteListRepository) {
	hx.post("/add") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()
		val page = params["page"]?.toIntOrNull() ?: 0

		if (email.isNotEmpty()) {
			whiteListRepository.addToWhiteList(email)
		}

		// If called via HTMX, return the updated fragment. Otherwise, redirect.
		val isHtmx = call.request.headers["HX-Request"] == "true"
		if (isHtmx) {
			val model = getWhitelistModel(call, whiteListRepository, page)
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
		val page = params["page"]?.toIntOrNull() ?: 0

		if (email.isNotEmpty()) {
			whiteListRepository.removeFromWhiteList(email)
		}

		val model = getWhitelistModel(call, whiteListRepository, page)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

private fun Route.whitelistUserFragment(whiteListRepository: WhiteListRepository) {
	hx.get("/user-fragment") {
		val model = getWhitelistModel(call, whiteListRepository)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

private suspend fun getWhitelistModel(
	call: ApplicationCall,
	whiteListRepository: WhiteListRepository,
	page: Int? = null
): MutableMap<String, Any> {
	val queryPage = call.request.queryParameters["page"]?.toIntOrNull()
	val actualPage = page ?: queryPage ?: 0

	val pageSize = 5
	val totalCount = whiteListRepository.getWhiteListCount()
	val totalPages = ceil(totalCount.toDouble() / pageSize).toInt()
	val currentPage = if (totalPages > 0) actualPage.coerceIn(0, totalPages - 1) else 0

	val whitelist = mutableMapOf<String, Any>()
	whitelist["items"] = whiteListRepository.getWhiteList(currentPage, pageSize)
	whitelist["currentPage"] = currentPage
	whitelist["currentPageDisplay"] = currentPage + 1
	whitelist["totalPages"] = totalPages
	whitelist["hasNextPage"] = currentPage < totalPages - 1
	whitelist["hasPrevPage"] = currentPage > 0
	whitelist["nextPage"] = currentPage + 1
	whitelist["prevPage"] = currentPage - 1
	whitelist["enabled"] = whiteListRepository.useWhiteList()

	val model = call.withDefaults()
	model["whitelist"] = whitelist

	return model
}

private fun Route.whitelistSettings(whiteListRepository: WhiteListRepository) {
	post("/settings") {
		val params = call.receiveParameters()
		val enabled = params["enabled"] != null
		val contact = params["contact"]

		whiteListRepository.setWhiteListEnabled(enabled)
		//whiteListRepository.setContactEmail(contact)
		error("Not Implemented yet")

		// Redirect to admin page after saving settings
		call.respondRedirect("/admin")
	}
}