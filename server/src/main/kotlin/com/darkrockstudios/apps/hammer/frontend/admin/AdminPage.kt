package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.email.EmailService
import com.darkrockstudios.apps.hammer.frontend.admin.*
import com.darkrockstudios.apps.hammer.frontend.utils.adminOnly
import com.darkrockstudios.apps.hammer.frontend.utils.formatSqliteDateTime
import com.darkrockstudios.apps.hammer.frontend.utils.formatSyncDate
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.patreon.PatreonSyncService
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.ResUtils
import io.ktor.htmx.*
import io.ktor.server.application.*
import io.ktor.server.htmx.*
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.math.ceil

fun Route.adminPage(
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	accountsRepository: AccountsRepository,
	projectsRepository: ProjectsRepository,
	serverConfig: ServerConfig,
	patreonSyncService: PatreonSyncService?,
	emailService: EmailService?
) {
	val patreonFeatureEnabled = serverConfig.patreonEnabled == true
	val emailFeatureEnabled = serverConfig.emailProviderType != null

	adminOnly {
		route("/admin") {
			adminSettingsPage(configRepository, patreonFeatureEnabled, emailFeatureEnabled)
			adminWhitelistPage(whiteListRepository, patreonFeatureEnabled, emailFeatureEnabled)
			adminUsersPage(patreonFeatureEnabled, emailFeatureEnabled)
			whiteListRoutes(whiteListRepository)
			serverSettingsRoutes(configRepository)
			usersRoutes(accountsRepository, projectsRepository)
			if (patreonFeatureEnabled && patreonSyncService != null) {
				adminPatreonPage(configRepository, patreonSyncService, emailFeatureEnabled)
				patreonSettingsRoutes(configRepository, patreonSyncService, serverConfig)
			}
			if (emailFeatureEnabled && emailService != null) {
				adminEmailPage(configRepository, emailService, patreonFeatureEnabled, serverConfig)
				emailSettingsRoutes(configRepository, emailService)
			}
		}
	}
}

// GET /admin - Server Settings page (default)
private fun Route.adminSettingsPage(
	configRepository: ConfigRepository,
	patreonFeatureEnabled: Boolean,
	emailFeatureEnabled: Boolean
) {
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
			"activeEmail" to false,
			"patreonFeatureEnabled" to patreonFeatureEnabled,
			"emailFeatureEnabled" to emailFeatureEnabled,
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
private fun Route.adminWhitelistPage(
	whiteListRepository: WhiteListRepository,
	patreonFeatureEnabled: Boolean,
	emailFeatureEnabled: Boolean
) {
	get("/whitelist") {
		val model = mapOf(
			"page_stylesheet" to "/assets/css/admin.css",
			"activeSettings" to false,
			"activeWhitelist" to true,
			"activeUsers" to false,
			"activePatreon" to false,
			"activeEmail" to false,
			"patreonFeatureEnabled" to patreonFeatureEnabled,
			"emailFeatureEnabled" to emailFeatureEnabled,
			"whitelist" to mapOf(
				"enabled" to whiteListRepository.useWhiteList()
			),
		)
		call.respond(MustacheContent("admin-whitelist.mustache", call.withDefaults(model)))
	}
}

// GET /admin/users - User Management page
private fun Route.adminUsersPage(patreonFeatureEnabled: Boolean, emailFeatureEnabled: Boolean) {
	get("/users") {
		val model = mapOf(
			"page_stylesheet" to "/assets/css/admin.css",
			"activeSettings" to false,
			"activeWhitelist" to false,
			"activeUsers" to true,
			"activePatreon" to false,
			"activeEmail" to false,
			"patreonFeatureEnabled" to patreonFeatureEnabled,
			"emailFeatureEnabled" to emailFeatureEnabled,
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
			"lastSync" to (formatLastSync(mostRecentSync) ?: call.msg("admin_patreon_last_sync_never")),
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
	return formatSqliteDateTime(sqliteDateTime, "MMM dd, yyyy")
}

private fun formatLastSync(sqliteDateTime: String?): String? {
	if (sqliteDateTime == null) return null
	return formatSyncDate(sqliteDateTime).ifEmpty { null }
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
