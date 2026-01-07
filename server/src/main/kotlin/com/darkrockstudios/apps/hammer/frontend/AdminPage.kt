package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.utils.adminOnly
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.patreon.PatreonApiClient
import com.darkrockstudios.apps.hammer.patreon.PatreonSyncService
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
import org.koin.ktor.ext.inject
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

fun Route.adminPage(
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	accountsRepository: AccountsRepository,
	projectsRepository: ProjectsRepository,
	serverConfig: ServerConfig,
	patreonSyncService: PatreonSyncService?
) {
	val patreonFeatureEnabled = serverConfig.patreonEnabled == true

	adminOnly {
		route("/admin") {
			adminSettingsPage(configRepository, patreonFeatureEnabled)
			adminWhitelistPage(whiteListRepository, patreonFeatureEnabled)
			adminUsersPage(patreonFeatureEnabled)
			whiteListRoutes(whiteListRepository)
			serverSettingsRoutes(configRepository)
			usersRoutes(accountsRepository, projectsRepository)
			if (patreonFeatureEnabled && patreonSyncService != null) {
				adminPatreonPage(configRepository, patreonSyncService)
				patreonSettingsRoutes(configRepository, patreonSyncService, serverConfig)
			}
		}
	}
}

// GET /admin - Server Settings page (default)
private fun Route.adminSettingsPage(configRepository: ConfigRepository, patreonFeatureEnabled: Boolean) {
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
			"activePatreon" to false,
			"patreonFeatureEnabled" to patreonFeatureEnabled,
			"contactEmail" to configRepository.get(AdminServerConfig.CONTACT_EMAIL),
			"serverMessage" to configRepository.get(AdminServerConfig.SERVER_MESSAGE),
			"aboutServer" to configRepository.get(AdminServerConfig.ABOUT_SERVER),
			"defaultLocale" to configuredDefaultLocale,
			"availableLocales" to availableLocales,
		)
		call.respond(MustacheContent("admin-settings.mustache", call.withDefaults(model)))
	}
}

// GET /admin/whitelist - Whitelist Management page
private fun Route.adminWhitelistPage(whiteListRepository: WhiteListRepository, patreonFeatureEnabled: Boolean) {
	get("/whitelist") {
		val model = mapOf(
			"page_stylesheet" to "/assets/css/admin.css",
			"activeSettings" to false,
			"activeWhitelist" to true,
			"activeUsers" to false,
			"activePatreon" to false,
			"patreonFeatureEnabled" to patreonFeatureEnabled,
			"whitelist" to mapOf(
				"enabled" to whiteListRepository.useWhiteList()
			),
		)
		call.respond(MustacheContent("admin-whitelist.mustache", call.withDefaults(model)))
	}
}

// GET /admin/users - User Management page
private fun Route.adminUsersPage(patreonFeatureEnabled: Boolean) {
	get("/users") {
		val model = mapOf(
			"page_stylesheet" to "/assets/css/admin.css",
			"activeSettings" to false,
			"activeWhitelist" to false,
			"activeUsers" to true,
			"activePatreon" to false,
			"patreonFeatureEnabled" to patreonFeatureEnabled,
		)
		call.respond(MustacheContent("admin-users.mustache", call.withDefaults(model)))
	}
}

// GET /admin/patreon - Patreon Integration page
private fun Route.adminPatreonPage(configRepository: ConfigRepository, patreonSyncService: PatreonSyncService) {
	get("/patreon") {
		val patreonConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)
		val patreonMemberCount = patreonSyncService.getPatreonWhitelistCount()
		val patreonMembersWithAccounts = patreonSyncService.getPatreonMembersWithAccountsCount()

		val model = mapOf(
			"page_stylesheet" to "/assets/css/admin.css",
			"activeSettings" to false,
			"activeWhitelist" to false,
			"activeUsers" to false,
			"activePatreon" to true,
			"patreonFeatureEnabled" to true,
			"patreonEnabled" to patreonConfig.enabled,
			"campaignId" to patreonConfig.campaignId,
			"accessToken" to patreonConfig.creatorAccessToken,
			"webhookSecret" to patreonConfig.webhookSecret,
			"minimumAmountDollars" to "%.2f".format(patreonConfig.minimumAmountCents / 100.0),
			"pollIntervalMinutes" to patreonConfig.pollIntervalMinutes,
			"lastSync" to patreonConfig.lastSync.ifEmpty { "Never" },
			"patreonMemberCount" to patreonMemberCount,
			"patreonMembersWithAccounts" to patreonMembersWithAccounts,
			"webhookUrl" to "/api/patreon/webhook"
		)
		call.respond(MustacheContent("admin-patreon.mustache", call.withDefaults(model)))
	}
}

private fun Route.patreonSettingsRoutes(
	configRepository: ConfigRepository,
	patreonSyncService: PatreonSyncService,
	serverConfig: ServerConfig
) {
	val patreonApiClient: PatreonApiClient by inject()

	route("/patreon") {
		// POST /admin/patreon/fetch-campaign - Fetch Campaign ID from Patreon API
		hx.post("/fetch-campaign") {
			val params = call.receiveParameters()
			val accessToken = params["accessToken"]?.trim().orEmpty()

			val currentConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)
			val effectiveToken = accessToken.ifEmpty { currentConfig.creatorAccessToken }

			if (effectiveToken.isEmpty()) {
				call.respondText(
					"<input id=\"campaignId\" name=\"campaignId\" type=\"text\" value=\"\" class=\"form-input form-input--error\" placeholder=\"${
						call.msg(
							"admin_patreon_error_token_required"
						)
					}\"/>",
					io.ktor.http.ContentType.Text.Html
				)
				return@post
			}

			val result = patreonApiClient.fetchCampaignId(effectiveToken)

			if (result.isSuccess) {
				val campaignId = result.getOrThrow()
				call.respondText(
					"<input id=\"campaignId\" name=\"campaignId\" type=\"text\" value=\"$campaignId\" class=\"form-input\" placeholder=\"123456\"/>",
					io.ktor.http.ContentType.Text.Html
				)
			} else {
				call.respondText(
					"<input id=\"campaignId\" name=\"campaignId\" type=\"text\" value=\"\" class=\"form-input form-input--error\" placeholder=\"${
						call.msg(
							"admin_patreon_error_fetch_failed"
						)
					}\"/>",
					io.ktor.http.ContentType.Text.Html
				)
			}
		}

		// POST /admin/patreon/settings - Save Patreon settings
		hx.post("/settings") {
			val params = call.receiveParameters()
			val enabled = params["enabled"] == "true"
			val campaignId = params["campaignId"]?.trim().orEmpty()
			val accessToken = params["accessToken"]?.trim().orEmpty()
			val webhookSecret = params["webhookSecret"]?.trim().orEmpty()
			val minimumAmountStr = params["minimumAmount"]?.trim().orEmpty()
			val pollIntervalStr = params["pollInterval"]?.trim().orEmpty()

			val currentConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)

			// Validation: if trying to enable, required fields must be filled
			if (enabled) {
				val effectiveAccessToken = accessToken.ifEmpty { currentConfig.creatorAccessToken }

				if (campaignId.isEmpty()) {
					call.response.header(HxResponseHeaders.Retarget, "#patreon-error")
					call.response.header(HxResponseHeaders.Reswap, "innerHTML")
					call.respondText(
						"<div class=\"error-message\">${call.msg("admin_patreon_error_campaign_required")}</div>",
						io.ktor.http.ContentType.Text.Html
					)
					return@post
				}

				if (effectiveAccessToken.isEmpty()) {
					call.response.header(HxResponseHeaders.Retarget, "#patreon-error")
					call.response.header(HxResponseHeaders.Reswap, "innerHTML")
					call.respondText(
						"<div class=\"error-message\">${call.msg("admin_patreon_error_token_required")}</div>",
						io.ktor.http.ContentType.Text.Html
					)
					return@post
				}
			}

			val minimumAmountCents = try {
				(minimumAmountStr.toDoubleOrNull()?.times(100))?.toInt() ?: 500
			} catch (_: Exception) {
				500
			}

			val pollIntervalMinutes = pollIntervalStr.toIntOrNull()?.coerceAtLeast(1) ?: 60

			val newConfig = currentConfig.copy(
				enabled = enabled,
				campaignId = campaignId,
				creatorAccessToken = accessToken.ifEmpty { currentConfig.creatorAccessToken },
				webhookSecret = webhookSecret.ifEmpty { currentConfig.webhookSecret },
				minimumAmountCents = minimumAmountCents,
				pollIntervalMinutes = pollIntervalMinutes
			)

			configRepository.set(AdminServerConfig.PATREON_CONFIG, newConfig)

			call.response.header(HxResponseHeaders.Refresh, "true")
			call.respond(io.ktor.http.HttpStatusCode.OK, "")
		}

		// POST /admin/patreon/sync - Trigger manual sync
		hx.post("/sync") {
			val config = configRepository.get(AdminServerConfig.PATREON_CONFIG)

			// Don't allow sync if not enabled
			if (!config.enabled) {
				call.response.header(HxResponseHeaders.Retarget, "#patreon-error")
				call.response.header(HxResponseHeaders.Reswap, "innerHTML")
				call.respondText(
					"<div class=\"error-message\">${call.msg("admin_patreon_error_not_enabled")}</div>",
					io.ktor.http.ContentType.Text.Html
				)
				return@post
			}

			val result = patreonSyncService.performFullSync()
			call.response.header(HxResponseHeaders.Refresh, "true")
			call.respond(io.ktor.http.HttpStatusCode.OK, "")
		}
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
		val about = params["about"]?.trim().orEmpty().take(4096)
		val defaultLocale = params["defaultLocale"]?.trim().orEmpty()

		configRepository.set(AdminServerConfig.CONTACT_EMAIL, contact)
		configRepository.set(AdminServerConfig.SERVER_MESSAGE, message)
		configRepository.set(AdminServerConfig.ABOUT_SERVER, about)
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
		val reason = params["reason"]?.trim().orEmpty()
		val page = params["page"]?.toIntOrNull() ?: 0

		// Validate email format
		if (email.isEmpty()) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_emailrequired")
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		if (!whiteListRepository.validateEmail(email)) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_emailinvalid")
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		val actualReason = reason.ifEmpty { "Added by admin" }

		// Validate reason length
		if (!whiteListRepository.validateReason(actualReason)) {
			val model = getWhitelistModelWithError(
				call, whiteListRepository, page,
				call.msg("admin_whitelist_error_reasontoolong")
			)
			call.respond(MustacheContent("partials/whitelist.mustache", model))
			return@post
		}

		// All validation passed
		whiteListRepository.addToWhiteList(email, actualReason)

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

	val whitelistEntries = whiteListRepository.getWhiteListWithDetails(currentPage, pageSize)
	val whitelistItems = whitelistEntries.map { entry ->
		mapOf(
			"email" to entry.email,
			"dateAdded" to formatDateFromTimestamp(entry.date_added),
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

	val model = call.withDefaults()
	model["whitelist"] = whitelist

	return model
}

private suspend fun getWhitelistModelWithError(
	call: ApplicationCall,
	whiteListRepository: WhiteListRepository,
	page: Int,
	errorMessage: String
): MutableMap<String, Any> {
	val model = getWhitelistModel(call, whiteListRepository, page)
	model["error"] = errorMessage
	return model
}

private fun formatDateFromTimestamp(epochSeconds: Long): String {
	return try {
		val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
		val zoned = java.time.Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault())
		formatter.format(zoned)
	} catch (e: Exception) {
		"Unknown"
	}
}
