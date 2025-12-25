package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.utils.adminOnly
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.ResUtils
import com.darkrockstudios.apps.hammer.utilities.sqliteDateTimeStringToInstant
import io.ktor.htmx.*
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

fun Route.adminPage(
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	accountsRepository: AccountsRepository,
	projectsRepository: ProjectsRepository
) {
	adminOnly {
		route("/admin") {
			adminSettingsPage(configRepository)
			adminWhitelistPage(whiteListRepository)
			adminUsersPage()
			whiteListRoutes(whiteListRepository)
			serverSettingsRoutes(configRepository)
			usersRoutes(accountsRepository, projectsRepository)
		}
	}
}

// GET /admin - Server Settings page (default)
private fun Route.adminSettingsPage(configRepository: ConfigRepository) {
	get {
		val configuredDefaultLocale = configRepository.get(AdminServerConfig.DEFAULT_LOCALE)
		val availableLocales = ResUtils.getTranslatedLocales().map { lc ->
			mapOf(
				"tag" to lc.toLanguageTag(),
				"label" to lc.getDisplayName(lc),
				"selected" to (lc.toLanguageTag() == configuredDefaultLocale)
			)
		}

		val model = mapOf(
			"page_stylesheet" to "/assets/css/admin.css",
			"activeSettings" to true,
			"activeWhitelist" to false,
			"activeUsers" to false,
			"contactEmail" to configRepository.get(AdminServerConfig.CONTACT_EMAIL),
			"serverMessage" to configRepository.get(AdminServerConfig.SERVER_MESSAGE),
			"defaultLocale" to configuredDefaultLocale,
			"availableLocales" to availableLocales,
		)
		call.respond(MustacheContent("admin-settings.mustache", call.withDefaults(model)))
	}
}

// GET /admin/whitelist - Whitelist Management page
private fun Route.adminWhitelistPage(whiteListRepository: WhiteListRepository) {
	get("/whitelist") {
		val model = mapOf(
			"page_stylesheet" to "/assets/css/admin.css",
			"activeSettings" to false,
			"activeWhitelist" to true,
			"activeUsers" to false,
			"whitelist" to mapOf(
				"enabled" to whiteListRepository.useWhiteList()
			),
		)
		call.respond(MustacheContent("admin-whitelist.mustache", call.withDefaults(model)))
	}
}

// GET /admin/users - User Management page
private fun Route.adminUsersPage() {
	get("/users") {
		val model = mapOf(
			"page_stylesheet" to "/assets/css/admin.css",
			"activeSettings" to false,
			"activeWhitelist" to false,
			"activeUsers" to true,
		)
		call.respond(MustacheContent("admin-users.mustache", call.withDefaults(model)))
	}
}

private fun Route.usersRoutes(accountsRepository: AccountsRepository, projectsRepository: ProjectsRepository) {
	route("/users") {
		usersFragment(accountsRepository, projectsRepository)
	}
}

private fun Route.usersFragment(accountsRepository: AccountsRepository, projectsRepository: ProjectsRepository) {
	hx.get("/user-fragment") {
		val model = getUsersModel(call, accountsRepository, projectsRepository)
		call.respond(MustacheContent("partials/users.mustache", model))
	}
}

private suspend fun getUsersModel(
	call: ApplicationCall,
	accountsRepository: AccountsRepository,
	projectsRepository: ProjectsRepository,
	page: Int? = null
): MutableMap<String, Any> {
	val queryPage = call.request.queryParameters["page"]?.toIntOrNull()
	val actualPage = page ?: queryPage ?: 0

	val pageSize = 10
	val totalCount = accountsRepository.numAccounts()
	val totalPages = ceil(totalCount.toDouble() / pageSize).toInt()
	val currentPage = if (totalPages > 0) actualPage.coerceIn(0, totalPages - 1) else 0

	val accounts = accountsRepository.getAccountsPaginated(currentPage, pageSize)
	val usersList = accounts.map { account ->
		val projectCount = projectsRepository.getProjectsCount(account.id)
		val mostRecentSync = projectsRepository.getMostRecentSyncForUser(account.id)
		mutableMapOf<String, Any?>(
			"email" to account.email,
			"created" to formatDate(account.created),
			"lastSync" to formatLastSync(mostRecentSync),
			"penName" to account.pen_name,
			"hasPenName" to (account.pen_name != null),
			"projectCount" to projectCount
		)
	}

	val users = mutableMapOf<String, Any>()
	users["items"] = usersList
	users["currentPage"] = currentPage
	users["currentPageDisplay"] = currentPage + 1
	users["totalPages"] = totalPages
	users["hasNextPage"] = currentPage < totalPages - 1
	users["hasPrevPage"] = currentPage > 0
	users["nextPage"] = currentPage + 1
	users["prevPage"] = currentPage - 1

	val model = call.withDefaults()
	model["users"] = users

	return model
}

private fun formatDate(sqliteDateTime: String): String {
	return try {
		val instant = sqliteDateTimeStringToInstant(sqliteDateTime)
		val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
		val zoned = java.time.Instant.ofEpochSecond(instant.epochSeconds).atZone(ZoneId.systemDefault())
		formatter.format(zoned)
	} catch (e: Exception) {
		sqliteDateTime
	}
}

private fun formatLastSync(sqliteDateTime: String?): String {
	if (sqliteDateTime == null) return "Never"
	return try {
		val instant = sqliteDateTimeStringToInstant(sqliteDateTime)
		val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")
		val zoned = java.time.Instant.ofEpochSecond(instant.epochSeconds).atZone(ZoneId.systemDefault())
		formatter.format(zoned)
	} catch (e: Exception) {
		"Never"
	}
}

private fun Route.serverSettingsRoutes(configRepository: ConfigRepository) {
	hx.post("/settings") {
		val params = call.receiveParameters()
		val contact = params["contact"]?.trim().orEmpty()
		val message = params["message"]?.trim().orEmpty()
		val defaultLocale = params["defaultLocale"]?.trim().orEmpty()

		configRepository.set(AdminServerConfig.CONTACT_EMAIL, contact)
		configRepository.set(AdminServerConfig.SERVER_MESSAGE, message)
		if (defaultLocale.isNotEmpty()) {
			configRepository.set(AdminServerConfig.DEFAULT_LOCALE, defaultLocale)
		}

		call.response.header(HxResponseHeaders.Refresh, "true")
		call.respond(io.ktor.http.HttpStatusCode.OK, "")
	}
}

private fun Route.whiteListRoutes(whiteListRepository: WhiteListRepository) {
	route("/whitelist") {
		whitelistUserFragment(whiteListRepository)
		whitelistAdd(whiteListRepository)
		whitelistRemove(whiteListRepository)
		whitelistToggle(whiteListRepository)
	}
}

private fun Route.whitelistToggle(whiteListRepository: WhiteListRepository) {
	hx.post("/toggle") {
		val enabled = whiteListRepository.useWhiteList()
		whiteListRepository.setWhiteListEnabled(!enabled)

		call.response.header(HxResponseHeaders.Refresh, "true")
		call.respond(io.ktor.http.HttpStatusCode.OK, "")
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

		val model = getWhitelistModel(call, whiteListRepository, page)
		call.respond(MustacheContent("partials/whitelist.mustache", model))
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

	val pageSize = 10
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
