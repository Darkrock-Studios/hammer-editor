package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ExtraLink
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
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECTS_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.dependencyinjection.PROJECT_SYNC_MANAGER
import com.darkrockstudios.apps.hammer.email.EmailService
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.og.OgImageService
import com.darkrockstudios.apps.hammer.frontend.og.ogImageRoutes
import com.darkrockstudios.apps.hammer.frontend.utils.canonicalUrl
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.withMessages
import com.darkrockstudios.apps.hammer.monitoring.ActivityType
import com.darkrockstudios.apps.hammer.monitoring.ErrorRepository
import com.darkrockstudios.apps.hammer.monitoring.MetricsRepository
import com.darkrockstudios.apps.hammer.monitoring.MonitoringState
import com.darkrockstudios.apps.hammer.monitoring.SecurityRepository
import com.darkrockstudios.apps.hammer.monitoring.StoryReaderCollector
import com.darkrockstudios.apps.hammer.monitoring.StoryReaderRepository
import com.darkrockstudios.apps.hammer.monitoring.UserActivityCollector
import com.darkrockstudios.apps.hammer.monitoring.UserActivityRepository
import com.darkrockstudios.apps.hammer.monitoring.isClientAbort
import com.darkrockstudios.apps.hammer.monitoring.recordMonitoredError
import com.darkrockstudios.apps.hammer.monitoring.toMonitoredStatus
import com.darkrockstudios.apps.hammer.patreon.PatreonSyncService
import com.darkrockstudios.apps.hammer.plugins.configureTemplating
import com.darkrockstudios.apps.hammer.project.ProjectSyncKey
import com.darkrockstudios.apps.hammer.project.ProjectSynchronizationSession
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsSynchronizationSession
import com.darkrockstudios.apps.hammer.secret.KeyringManager
import com.darkrockstudios.apps.hammer.story.StoryRendererService
import com.darkrockstudios.apps.hammer.syncsessionmanager.SyncSessionManager
import com.darkrockstudios.apps.hammer.utilities.MarkdownService
import com.darkrockstudios.apps.hammer.utilities.ServerSecretManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.session
import io.ktor.server.http.content.ETagProvider
import io.ktor.server.http.content.staticResources
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.sessions.SessionTransportTransformerEncrypt
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.coroutines.runBlocking
import org.koin.core.qualifier.named
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject
import java.security.MessageDigest
import java.util.Locale
import kotlin.time.Duration.Companion.days

fun Route.frontend() {
	val accountsRepository: AccountsRepository by inject()
	val whiteListRepository: WhiteListRepository by inject()
	val configRepository: ConfigRepository by inject()
	val projectsRepository: ProjectsRepository by inject()
	val storyRendererService: StoryRendererService by inject()
	val projectAccessRepository: ProjectAccessRepository by inject()
	val penNameService: PenNameService by inject()
	val bioService: BioService by inject()
	val serverConfig: ServerConfig by inject()
	val passwordResetRepository: PasswordResetRepository by inject()
	val markdownService: MarkdownService by inject()
	val reviewRepository: com.darkrockstudios.apps.hammer.review.ReviewRepository by inject()
	val projectDao: com.darkrockstudios.apps.hammer.database.ProjectDao by inject()
	val metricsRepository: MetricsRepository by inject()
	val errorRepository: ErrorRepository by inject()
	val securityRepository: SecurityRepository by inject()
	val userActivityRepository: UserActivityRepository by inject()
	val storyReaderRepository: StoryReaderRepository by inject()
	val storyReaderCollector: StoryReaderCollector by inject()
	val recurringTaskRegistry: com.darkrockstudios.apps.hammer.scheduling.RecurringTaskRegistry by inject()
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

	robotsRoutes()
	sitemapRoutes(serverConfig, accountsRepository, projectAccessRepository, configRepository)
	setupPage(serverConfig)
	homePage(whiteListRepository, configRepository, serverConfig, accountsRepository, projectAccessRepository)
	aboutPage(configRepository, serverConfig, accountsRepository, projectAccessRepository, markdownService)
	termsOfServicePage()
	privacyPolicyPage()
	localeRoutes()
	authRoutes(accountsRepository, whiteListRepository, configRepository, serverConfig)
	passwordResetRoutes(passwordResetRepository)
	dashboardPage(projectsRepository, accountsRepository, penNameService, bioService, serverConfig, markdownService)
	storyPage(
		storyRendererService,
		projectAccessRepository,
		projectsRepository,
		accountsRepository,
		reviewRepository,
		storyReaderRepository, projectDao, clock,
	)
	reviewFrontend(
		reviewRepository = reviewRepository,
		projectsRepository = projectsRepository,
		storyRendererService = storyRendererService,
		accountsRepository = accountsRepository,
		projectDao = projectDao,
		markdownService = markdownService,
		reviewInviteMailer = emailService?.let { com.darkrockstudios.apps.hammer.review.ReviewInviteMailer(it) },
		reviewSubmittedMailer = emailService?.let { com.darkrockstudios.apps.hammer.review.ReviewSubmittedMailer(it) },
		clock = clock,
	)
	authorPage(accountsRepository, projectAccessRepository, markdownService, serverConfig)
	publicStoryPage(
		storyRendererService,
		projectAccessRepository,
		projectDao,
		storyReaderCollector,
		accountsRepository,
		projectsRepository,
		serverConfig,
	)
	if (serverConfig.richLinkPreviews) {
		val ogImageService by inject<OgImageService>()
		ogImageRoutes(accountsRepository, projectAccessRepository, ogImageService)
	}
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
		securityRepository,
		userActivityRepository,
		storyReaderRepository,
		recurringTaskRegistry,
		projectsSyncManager,
		projectSyncManager,
		clock,
	)
	communityPage(projectAccessRepository, accountsRepository, serverConfig)
}

const val COOKIE_USER_SESSION = "user_session"

/**
 * Encrypts and authenticates the web session cookie so a client cannot forge or
 * tamper with [UserSession]. Encrypt-then-MAC: a modified or fabricated cookie
 * fails the MAC and is dropped, leaving the request unauthenticated.
 *
 * Two distinct AES and HMAC keys are derived from the same secret via SHA-256
 * with domain separation. Rotating the underlying secret invalidates all existing
 * web sessions, forcing a re-login.
 */
internal fun userSessionTransformer(keyMaterial: ByteArray): SessionTransportTransformerEncrypt {
	// AES-128: the transformer generates an IV sized to the encryption key, and
	// AES/CBC requires a 16-byte IV. The sign key has no length constraint.
	val encryptionKey = deriveKey(keyMaterial, "hammer.web.session.encrypt").copyOf(16)
	val signKey = deriveKey(keyMaterial, "hammer.web.session.sign")
	return SessionTransportTransformerEncrypt(encryptionKey, signKey)
}

private fun deriveKey(keyMaterial: ByteArray, label: String): ByteArray {
	val digest = MessageDigest.getInstance("SHA-256")
	digest.update(keyMaterial)
	digest.update(label.toByteArray(Charsets.UTF_8))
	return digest.digest()
}

fun Application.configureFrontEnd() {
	configureTemplating()

	val errorRepository: ErrorRepository by inject()
	val monitoringState: MonitoringState by inject()
	val keyringManager: KeyringManager by inject()
	val serverSecretManager: ServerSecretManager by inject()

	val sessionKeyMaterial = runBlocking {
		keyringManager.tokenHmacKeyOrNull() ?: serverSecretManager.getServerSecret()
	}.toByteArray(Charsets.UTF_8)

	install(plugin = Sessions) {
		cookie<UserSession>(COOKIE_USER_SESSION) {
			cookie.path = "/"
			cookie.maxAgeInSeconds = 7.days.inWholeSeconds
			cookie.extensions["SameSite"] = "lax"
			transform(userSessionTransformer(sessionKeyMaterial))
		}
	}

	configureStatusPages(errorRepository, monitoringState)
}

/** Only the error and setup templates use error.css, so it is not part of the shared header. */
internal val ERROR_PAGE_STYLE = mapOf("page_stylesheet" to "/assets/css/error.css")

fun Application.configureStatusPages(
	errorRepository: ErrorRepository,
	monitoringState: MonitoringState,
) {
	fun ApplicationRequest.isApiCall(): Boolean = path().startsWith("/$API_ROUTE_PREFIX/")

	install(StatusPages) {
		status(HttpStatusCode.NotFound) { call, status ->
			if (call.request.isApiCall()) {
				call.respond(HttpStatusCode.NotFound)
			} else {
				call.respond(HttpStatusCode.NotFound, MustacheContent("notfound.mustache", call.withDefaults(ERROR_PAGE_STYLE)))
			}
		}
		status(HttpStatusCode.Unauthorized) { call, status ->
			if (call.request.isApiCall()) {
				call.respond(HttpStatusCode.Unauthorized)
			} else {
				call.respond(
					HttpStatusCode.Unauthorized,
					MustacheContent("unauthorized.mustache", call.withDefaults(ERROR_PAGE_STYLE))
				)
			}
		}
		exception<Throwable> { call, cause ->
			// A client abort is not a server fault: skip the error log and
			// monitoring, and don't try to respond down a dead stream.
			if (cause.isClientAbort()) {
				call.application.log.debug(
					"Client aborted response for ${call.request.httpMethod.value} ${call.request.path()}"
				)
				return@exception
			}
			call.application.log.error(
				"Unhandled exception on ${call.request.httpMethod.value} ${call.request.path()}",
				cause
			)
			if (call.request.isApiCall()) {
				val status = HttpStatusCode.fromValue(cause.toMonitoredStatus())
				recordMonitoredError(call, cause, status.value, errorRepository, monitoringState)
				call.respond(status)
			} else {
				recordMonitoredError(
					call,
					cause,
					HttpStatusCode.InternalServerError.value,
					errorRepository,
					monitoringState
				)
				call.respond(
					HttpStatusCode.InternalServerError,
					MustacheContent("servererror.mustache", call.withDefaults(ERROR_PAGE_STYLE))
				)
			}
		}
	}
}

const val SESSION_AUTH = "auth-session"

fun AuthenticationConfig.frontendAuthentication(accountRepo: AccountsRepository, whitelistRepo: WhiteListRepository) {
	session<UserSession>(SESSION_AUTH) {
		validate { session ->
			if (sessionIsAuthorized(session, accountRepo, whitelistRepo)) session else null
		}
		challenge {
			call.respondRedirect("/login")
		}
	}
}

/**
 * Whether [session] should reach whitelist-protected pages: the account must exist
 * and either be an admin or, when the whitelist is active, be on it. Admin status is
 * read from the account row, not the cookie, so a revoked admin can't ride a stale
 * session past the gate. Must match the redirect gate in the login page — if the two
 * disagree, a logged-in but unauthorized user loops between /login and /dashboard.
 */
suspend fun sessionIsAuthorized(
	session: UserSession,
	accountRepo: AccountsRepository,
	whitelistRepo: WhiteListRepository,
): Boolean {
	val account = runCatching { accountRepo.getAccount(session.userId) }.getOrNull()
	return when {
		account == null -> false
		account.is_admin -> true
		whitelistRepo.useWhiteList() -> whitelistRepo.isOnWhiteList(account.email)
		else -> true
	}
}

private fun ExtraLink.toModel(locale: Locale): Map<String, Any> = mapOf(
	"url" to url,
	"icon" to icon,
	"title" to title(locale),
	"external" to isExternal,
)

fun MutableMap<String, Any>.addDefaults(): MutableMap<String, Any> {
	this["version"] = BuildMetadata.APP_VERSION
	return this
}

suspend fun ApplicationCall.withDefaults(data: Map<String, Any> = emptyMap()): MutableMap<String, Any> {
	val model = withMessages(data).addDefaults()
	model.putIfAbsent("title", msg("page_title"))
	// Self-referential canonical from the request path (query stripped). Pages whose query
	// params are content-bearing (e.g. story pagination) override this with their own value.
	model.putIfAbsent("canonicalUrl", canonicalUrl())
	// OpenGraph / Twitter card defaults; pages override ogType (profile/article) and may
	// override ogImage. Title, description, and url reuse the fields set above.
	model.putIfAbsent("ogType", "website")
	model.putIfAbsent("ogImage", canonicalUrl("/assets/images/og-default.png"))
	val session = sessions.get<UserSession>()
	if (session != null) {
		model["isLoggedIn"] = true
		model["sessionUsername"] = session.username
		get<UserActivityCollector>().record(session.userId, ActivityType.WEB)
	} else {
		model["isLoggedIn"] = false
	}

	val serverConfig = get<ServerConfig>()

	val configRepository = get<ConfigRepository>()
	val aboutContent = configRepository.get(AdminServerConfig.ABOUT_SERVER)
	val hasAboutPage = aboutContent.isNotBlank()
	// Footer visibility keys off config, never a file read, so page renders stay off the filesystem.
	val hasTermsPage = serverConfig.termsOfService != null
	val hasPrivacyPage = serverConfig.privacyPolicy != null
	model["hasAboutPage"] = hasAboutPage
	model["hasTermsPage"] = hasTermsPage
	model["hasPrivacyPage"] = hasPrivacyPage

	// withMessages() already resolved the viewer's locale; re-resolving repeats its config
	// lookup, which is an uncached query for a request with no cookie or Accept-Language.
	val locale = (model["locale"] as? String)?.let(Locale::forLanguageTag) ?: Locale.ENGLISH
	val footerExtraLinks = serverConfig.extraLinks.filter { it.placement.inFooter }.map { it.toModel(locale) }
	model["headerExtraLinks"] = serverConfig.extraLinks.filter { it.placement.inHeader }.map { it.toModel(locale) }
	model["footerExtraLinks"] = footerExtraLinks
	model["hasFooterNav"] = hasAboutPage || hasTermsPage || hasPrivacyPage || footerExtraLinks.isNotEmpty()

	// Add Patreon link for footer if configured
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
			model["analyticsEnabled"] = true
			model["analyticsHead"] = provider.headSnippet()
			model["analyticsData"] = provider.clientConfig().map { (name, value) ->
				mapOf("name" to name, "value" to value)
			}
		}
	}

	return model
}