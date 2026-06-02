package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.SortDirection
import com.darkrockstudios.apps.hammer.account.UserSortField
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.email.EmailService
import com.darkrockstudios.apps.hammer.frontend.admin.*
import com.darkrockstudios.apps.hammer.frontend.utils.adminOnly
import com.darkrockstudios.apps.hammer.frontend.utils.formatInstant
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
	emailService: EmailService?,
	metricsRepository: com.darkrockstudios.apps.hammer.monitoring.MetricsRepository,
	projectsSyncManager: com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager<Long, com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession>,
	projectSyncManager: com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager<*, com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession>,
	clock: kotlin.time.Clock,
) {
	val patreonFeatureEnabled = serverConfig.patreonEnabled == true
	val emailFeatureEnabled = serverConfig.emailProviderType != null

	adminOnly {
		route("/admin") {
			adminSettingsPage(configRepository, patreonFeatureEnabled, emailFeatureEnabled)
			adminMonitoringPages(
				metricsRepository, configRepository, projectsSyncManager, projectSyncManager,
				clock, patreonFeatureEnabled, emailFeatureEnabled,
			)
			adminWhitelistPage(
				whiteListRepository,
				configRepository,
				serverConfig,
				patreonFeatureEnabled,
				emailFeatureEnabled
			)
			adminUsersPage(patreonFeatureEnabled, emailFeatureEnabled)
			whiteListRoutes(whiteListRepository, configRepository, serverConfig)
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

		val mon = configRepository.get(AdminServerConfig.MONITORING_CONFIG)
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
			"monEnabled" to mon.enabled,
			"monTrackApiMetrics" to mon.trackApiMetrics,
			"monTrackErrors" to mon.trackErrors,
			"monTrackLoginAttempts" to mon.trackLoginAttempts,
			"monStoreLoginIp" to mon.storeLoginIp,
			"monPrometheus" to mon.prometheusEndpointEnabled,
			"monMetricsRetention" to mon.metricsRetentionDays,
			"monErrorRetention" to mon.errorRetentionDays,
			"monLoginRetention" to mon.loginAttemptRetentionDays,
			"monAlertEmailEnabled" to mon.alertEmailEnabled,
			"monAlertEmail" to mon.alertEmail,
		)
		call.respond(MustacheContent("admin-settings.mustache", call.withDefaults(model)))
	}
}

// GET /admin/whitelist - Whitelist Management page
private fun Route.adminWhitelistPage(
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	serverConfig: ServerConfig,
	patreonFeatureEnabled: Boolean,
	emailFeatureEnabled: Boolean
) {
	get("/whitelist") {
		val whitelistEnabled = whiteListRepository.useWhiteList()
		val patreonConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)
		val patreonActive = patreonFeatureEnabled && patreonConfig.enabled && patreonConfig.patreonUrl.isNotBlank()
		val canDisableWhitelist = !patreonActive

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
				"enabled" to whitelistEnabled,
				"canDisable" to canDisableWhitelist
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
	page: Int? = null,
	sortBy: UserSortField? = null,
	sortDirection: SortDirection? = null
): MutableMap<String, Any> {
	val queryPage = call.request.queryParameters["page"]?.toIntOrNull()
	val actualPage = page ?: queryPage ?: 0

	val querySortBy = call.request.queryParameters["sortBy"]?.let { UserSortField.fromString(it) }
	val actualSortBy = sortBy ?: querySortBy ?: UserSortField.CREATED

	val querySortDirection = call.request.queryParameters["sortDirection"]?.let { SortDirection.fromString(it) }
	val actualSortDirection = sortDirection ?: querySortDirection ?: SortDirection.DESCENDING

	val pageSize = 10
	val totalCount = accountsRepository.numAccounts()
	val totalPages = ceil(totalCount.toDouble() / pageSize).toInt()
	val currentPage = if (totalPages > 0) actualPage.coerceIn(0, totalPages - 1) else 0

	val accounts = accountsRepository.getAccountsPaginated(currentPage, pageSize, actualSortBy, actualSortDirection)
	val usersList = accounts.map { account ->
		mutableMapOf<String, Any?>(
			"email" to account.email,
			"created" to formatDate(account.created),
			"lastSync" to (formatLastSync(account.most_recent_sync) ?: call.msg("admin_patreon_last_sync_never")),
			"penName" to account.pen_name,
			"hasPenName" to (account.pen_name != null),
			"projectCount" to account.project_count
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
	users["sortBy"] = actualSortBy.value
	users["sortDirection"] = actualSortDirection.value
	users["sortByCreated"] = (actualSortBy == UserSortField.CREATED)
	users["sortByLastSync"] = (actualSortBy == UserSortField.LAST_SYNC)
	users["sortByProjectCount"] = (actualSortBy == UserSortField.PROJECT_COUNT)
	users["sortAscending"] = (actualSortDirection == SortDirection.ASCENDING)
	users["sortDescending"] = (actualSortDirection == SortDirection.DESCENDING)

	val model = call.withDefaults()
	model["users"] = users

	return model
}

private fun formatDate(instant: kotlin.time.Instant): String {
	return formatInstant(instant, "MMM dd, yyyy")
}

private fun formatLastSync(instant: kotlin.time.Instant?): String? {
	if (instant == null) return null
	return formatSyncDate(instant).ifEmpty { null }
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

		// Monitoring config. Checkboxes only POST when checked, so absence = false.
		val current = configRepository.get(AdminServerConfig.MONITORING_CONFIG)
		configRepository.set(
			AdminServerConfig.MONITORING_CONFIG,
			current.copy(
				enabled = params["monEnabled"] != null,
				trackApiMetrics = params["monTrackApiMetrics"] != null,
				trackErrors = params["monTrackErrors"] != null,
				trackLoginAttempts = params["monTrackLoginAttempts"] != null,
				storeLoginIp = params["monStoreLoginIp"] != null,
				prometheusEndpointEnabled = params["monPrometheus"] != null,
				alertEmailEnabled = params["monAlertEmailEnabled"] != null,
				alertEmail = params["monAlertEmail"]?.trim().orEmpty(),
				metricsRetentionDays = params["monMetricsRetention"]?.toIntOrNull()?.coerceIn(1, 3650)
					?: current.metricsRetentionDays,
				errorRetentionDays = params["monErrorRetention"]?.toIntOrNull()?.coerceIn(1, 3650)
					?: current.errorRetentionDays,
				loginAttemptRetentionDays = params["monLoginRetention"]?.toIntOrNull()?.coerceIn(1, 3650)
					?: current.loginAttemptRetentionDays,
			),
		)

		call.response.header(HxResponseHeaders.Refresh, "true")
		call.respond(io.ktor.http.HttpStatusCode.OK, "")
	}
}
