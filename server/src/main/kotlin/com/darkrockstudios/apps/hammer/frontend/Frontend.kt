package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.BioService
import com.darkrockstudios.apps.hammer.account.PasswordResetRepository
import com.darkrockstudios.apps.hammer.account.PenNameService
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.API_ROUTE_PREFIX
import com.darkrockstudios.apps.hammer.email.EmailService
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECT_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.frontend.utils.withMessages
import com.darkrockstudios.apps.hammer.monitoring.ErrorRepository
import com.darkrockstudios.apps.hammer.monitoring.MetricsRepository
import com.darkrockstudios.apps.hammer.monitoring.MonitoringState
import com.darkrockstudios.apps.hammer.monitoring.recordMonitoredError
import com.darkrockstudios.apps.hammer.patreon.PatreonSyncService
import com.darkrockstudios.apps.hammer.plugins.configureTemplating
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.story.StoryExportService
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.mustache.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.koin.core.qualifier.named
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.days

fun Route.frontend() {
	val accountsRepository: AccountsRepository by inject()
	val whiteListRepository: WhiteListRepository by inject()
	val configRepository: ConfigRepository by inject()
	val projectsRepository: ProjectsRepository by inject()
	val storyExportService: StoryExportService by inject()
	val projectAccessRepository: ProjectAccessRepository by inject()
	val penNameService: PenNameService by inject()
	val bioService: BioService by inject()
	val serverConfig: ServerConfig by inject()
	val passwordResetRepository: PasswordResetRepository by inject()
	val markdownService: MarkdownService by inject()
	val metricsRepository: MetricsRepository by inject()
	val errorRepository: ErrorRepository by inject()
	val monitoringState: MonitoringState by inject()
	val clock: kotlin.time.Clock by inject()
	val projectsSyncManager: SyncSessionManager<Long, ProjectsSynchronizationSession> by inject(named(PROJECTS_SYNC_MANAGER))
	val projectSyncManager: SyncSessionManager<ProjectSyncKey, ProjectSynchronizationSession> by inject(named(PROJECT_SYNC_MANAGER))

	// Only inject PatreonSyncService if Patreon is enabled at server level
	val patreonSyncService: PatreonSyncService? = if (serverConfig.patreonEnabled == true) {
		inject<PatreonSyncService>().value
	} else {
		null
	}

	// Only inject EmailService if email is enabled at server level
	val emailService: EmailService? = if (serverConfig.emailProviderType != null) {
		inject<EmailService>().value
	} else {
		null
	}

	staticResources("/assets", "/assets") {
		etag(ETagProvider.StrongSha256)
	}

	setupPage(serverConfig)
	homePage(whiteListRepository, configRepository, serverConfig, accountsRepository, projectAccessRepository)
	aboutPage(configRepository, serverConfig, accountsRepository, projectAccessRepository, markdownService)
	localeRoutes()
	authRoutes(accountsRepository, whiteListRepository, configRepository, serverConfig)
	passwordResetRoutes(passwordResetRepository)
	dashboardPage(projectsRepository, accountsRepository, penNameService, bioService, serverConfig, markdownService)
	storyPage(storyExportService, projectAccessRepository, projectsRepository, accountsRepository)
	authorPage(accountsRepository, projectAccessRepository, markdownService)
	publicStoryPage(storyExportService, projectAccessRepository)
	adminPage(
		whiteListRepository,
		configRepository,
		accountsRepository,
		projectsRepository,
		serverConfig,
		patreonSyncService,
		emailService,
		metricsRepository,
		errorRepository,
		projectsSyncManager,
		projectSyncManager,
		clock,
	)
	communityPage(projectAccessRepository, accountsRepository, serverConfig)
}

const val COOKIE_USER_SESSION = "user_session"

fun Application.configureFrontEnd() {
	configureTemplating()

	install(plugin = Sessions) {
		cookie<UserSession>(COOKIE_USER_SESSION) {
			cookie.path = "/"
			cookie.maxAgeInSeconds = 7.days.inWholeSeconds
			cookie.extensions["SameSite"] = "lax"
		}
	}

	fun ApplicationRequest.isApiCall(): Boolean = path().startsWith("/$API_ROUTE_PREFIX/")

	install(StatusPages) {
		status(HttpStatusCode.NotFound) { call, status ->
			if (call.request.isApiCall()) {
				call.respond(HttpStatusCode.NotFound)
			} else {
				call.respond(HttpStatusCode.NotFound, MustacheContent("notfound.mustache", call.withDefaults()))
			}
		}
		status(HttpStatusCode.Unauthorized) { call, status ->
			if (call.request.isApiCall()) {
				call.respond(HttpStatusCode.Unauthorized)
			} else {
				call.respond(HttpStatusCode.Unauthorized, MustacheContent("unauthorized.mustache", call.withDefaults()))
			}
		}
		exception<Throwable> { call, cause ->
			call.application.log.error(
				"Unhandled exception on ${call.request.httpMethod.value} ${call.request.path()}",
				cause
			)
			recordMonitoredError(call, cause, errorRepository, monitoringState)
			if (call.request.isApiCall()) {
				call.respond(HttpStatusCode.InternalServerError)
			} else {
				call.respond(
					HttpStatusCode.InternalServerError,
					MustacheContent("servererror.mustache", call.withDefaults())
				)
			}
		}
	}
}

const val SESSION_AUTH = "auth-session"

fun AuthenticationConfig.frontendAuthentication(accountRepo: AccountsRepository, whitelistRepo: WhiteListRepository) {
	session<UserSession>(SESSION_AUTH) {
		validate { session ->
			val account = accountRepo.getAccount(session.userId)
			if (whitelistRepo.useWhiteList()) {
				whitelistRepo.isOnWhiteList(account.email)
			} else {
				true
			}
		}
		challenge {
			call.respondRedirect("/login")
		}
	}
}

fun MutableMap<String, Any>.addDefaults(): MutableMap<String, Any> {
	this["version"] = BuildMetadata.APP_VERSION
	return this
}

suspend fun ApplicationCall.withDefaults(data: Map<String, Any> = emptyMap()): MutableMap<String, Any> {
	val model = withMessages(data).addDefaults()
	val session = sessions.get<UserSession>()
	if (session != null) {
		model["isLoggedIn"] = true
		model["sessionUsername"] = session.username
	} else {
		model["isLoggedIn"] = false
	}

	val configRepository = get<ConfigRepository>()
	val aboutContent = configRepository.get(AdminServerConfig.ABOUT_SERVER)
	model["hasAboutPage"] = aboutContent.isNotBlank()

	// Add Patreon link for footer if configured
	val serverConfig = get<ServerConfig>()
	if (serverConfig.patreonEnabled == true) {
		val patreonConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)
		if (patreonConfig.enabled && patreonConfig.patreonUrl.isNotBlank()) {
			model["patreonUrl"] = patreonConfig.patreonUrl
		}
	}

	// Add community enabled flag for header nav
	if (serverConfig.communityEnabled) {
		model["communityEnabled"] = true
	}

	// Add development mode flag for header banner
	if (application.developmentMode) {
		model["isDev"] = true
	}

	// Inject web analytics on public (logged-out) pages only
	if (session == null) {
		serverConfig.analytics.provider?.let { provider ->
			model["analyticsHead"] = provider.headSnippet()
		}
	}

	return model
}