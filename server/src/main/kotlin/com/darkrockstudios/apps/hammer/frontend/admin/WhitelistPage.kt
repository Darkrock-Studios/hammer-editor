package com.darkrockstudios.apps.hammer.frontend.admin

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.withDefaults
import io.ktor.htmx.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

internal fun Route.whiteListRoutes(
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	serverConfig: ServerConfig
) {
	route("/whitelist") {
		whitelistUserFragment(whiteListRepository)
		whitelistAdd(whiteListRepository)
		whitelistRemove(whiteListRepository)
		whitelistToggle(whiteListRepository, configRepository, serverConfig)
		whitelistEditReason(whiteListRepository)
	}
}

private suspend fun isPatreonActive(configRepository: ConfigRepository, serverConfig: ServerConfig): Boolean {
	val patreonConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)
	val patreonFeatureEnabled = serverConfig.patreonEnabled == true
	return patreonFeatureEnabled && patreonConfig.enabled && patreonConfig.patreonUrl.isNotBlank()
}

private fun Route.whitelistToggle(
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	serverConfig: ServerConfig
) {
	hx.post("/toggle") {
		val enabled = whiteListRepository.useWhiteList()
		val patreonActive = isPatreonActive(configRepository, serverConfig)

		// Prevent disabling whitelist when Patreon is active
		if (enabled && patreonActive) {
			call.respond(HttpStatusCode.Forbidden, "")
			return@post
		}

		whiteListRepository.setWhiteListEnabled(!enabled)

		call.response.header(HxResponseHeaders.Refresh, "true")
		call.respond(HttpStatusCode.OK, "")
	}
}

@OptIn(ExperimentalKtorApi::class)
private fun Route.whitelistAdd(whiteListRepository: WhiteListRepository) {
	hx.post("/add") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()
		val reason = params["reason"]?.trim().orEmpty()
		val page = params["page"]?.toIntOrNull() ?: 0
		val sortOldestFirst = params["sortOldestFirst"]?.toBoolean() ?: false

		// Validate email format
		if (email.isEmpty()) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_emailrequired"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		if (!whiteListRepository.validateEmail(email)) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_emailinvalid"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		val actualReason = reason.ifEmpty { "Added by admin" }

		// Validate reason length
		if (!whiteListRepository.validateReason(actualReason)) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_reasontoolong"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		// All validation passed
		whiteListRepository.addToWhiteList(email, actualReason)

		val model = getWhitelistModel(call, whiteListRepository, page, sortOldestFirst)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

private fun Route.whitelistRemove(whiteListRepository: WhiteListRepository) {
	hx.post("/remove") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()
		val page = params["page"]?.toIntOrNull() ?: 0
		val sortOldestFirst = params["sortOldestFirst"]?.toBoolean() ?: false

		if (email.isNotEmpty()) {
			whiteListRepository.removeFromWhiteList(email)
		}

		val model = getWhitelistModel(call, whiteListRepository, page, sortOldestFirst)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

private fun Route.whitelistEditReason(whiteListRepository: WhiteListRepository) {
	hx.post("/edit-reason") {
		val params = call.receiveParameters()
		val email = params["email"]?.trim().orEmpty()
		val reason = params["reason"]?.trim().orEmpty()
		val page = params["page"]?.toIntOrNull() ?: 0
		val sortOldestFirst = params["sortOldestFirst"]?.toBoolean() ?: false

		if (email.isEmpty()) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_emailrequired"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		if (!whiteListRepository.validateReason(reason)) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_reasontoolong"),
				sortOldestFirst
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		whiteListRepository.updateReason(email, reason)

		val model = getWhitelistModel(call, whiteListRepository, page, sortOldestFirst)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

internal fun Route.whitelistUserFragment(whiteListRepository: WhiteListRepository) {
	hx.get("/user-fragment") {
		val model = getWhitelistModel(call, whiteListRepository)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
	}
}

internal suspend fun getWhitelistModel(
	call: ApplicationCall,
	whiteListRepository: WhiteListRepository,
	page: Int? = null,
	sortOldestFirst: Boolean? = null
): MutableMap<String, Any> {
	val queryPage = call.request.queryParameters["page"]?.toIntOrNull()
	val actualPage = page ?: queryPage ?: 0

	val querySortOldestFirst = call.request.queryParameters["sortOldestFirst"]?.toBoolean()
	val actualSortOldestFirst = sortOldestFirst ?: querySortOldestFirst ?: false

	val pageSize = 10
	val totalCount = whiteListRepository.getWhiteListCount()
	val totalPages = ceil(totalCount.toDouble() / pageSize).toInt()
	val currentPage = if (totalPages > 0) actualPage.coerceIn(0, totalPages - 1) else 0

	val whitelistEntries = whiteListRepository.getWhiteListWithDetails(currentPage, pageSize, actualSortOldestFirst)
	val whitelistItems = whitelistEntries.map { entry ->
		mapOf(
			"email" to entry.email,
			"dateAdded" to (formatDateFromTimestamp(entry.date_added)
				?: call.msg("admin_whitelist_date_added_unknown")),
			"reason" to entry.reason
		)
	}

	val whitelist = mutableMapOf<String, Any>()
	whitelist["items"] = whitelistItems
	whitelist["currentPage"] = currentPage
	whitelist["currentPageDisplay"] = currentPage + 1
	whitelist["totalPages"] = totalPages
	whitelist["hasNextPage"] = currentPage < totalPages - 1
	whitelist["hasPrevPage"] = currentPage > 0
	whitelist["nextPage"] = currentPage + 1
	whitelist["prevPage"] = currentPage - 1
	whitelist["enabled"] = whiteListRepository.useWhiteList()
	whitelist["sortOldestFirst"] = actualSortOldestFirst
	whitelist["sortNewestFirst"] = !actualSortOldestFirst

	val model = call.withDefaults()
	model["whitelist"] = whitelist

	return model
}

private suspend fun getWhitelistModelWithError(
	call: ApplicationCall,
	whiteListRepository: WhiteListRepository,
	page: Int,
	errorMessage: String,
	sortOldestFirst: Boolean? = null
): MutableMap<String, Any> {
	val model = getWhitelistModel(call, whiteListRepository, page, sortOldestFirst)
	model["error"] = errorMessage
	return model
}

private fun formatDateFromTimestamp(epochSeconds: Long): String? {
	return try {
		val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
		val zoned = java.time.Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault())
		formatter.format(zoned)
	} catch (e: Exception) {
		null
	}
}