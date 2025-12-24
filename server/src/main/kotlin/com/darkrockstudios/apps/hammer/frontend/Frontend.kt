package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.PenNameService
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.base.BuildMetadata
import com.darkrockstudios.apps.hammer.base.http.API_ROUTE_PREFIX
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.withMessages
import com.darkrockstudios.apps.hammer.plugins.configureTemplating
import com.darkrockstudios.apps.hammer.project.access.ProjectAccessRepository
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.story.StoryExportService
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

	staticResources("/assets", "/assets")

	homePage(whiteListRepository, configRepository)
	localeRoutes()
	authRoutes(accountsRepository, whiteListRepository, configRepository)
	dashboardPage(projectsRepository, accountsRepository, penNameService)
	storyPage(storyExportService, projectAccessRepository, projectsRepository, accountsRepository)
	authorPage(accountsRepository, projectAccessRepository)
	publicStoryPage(storyExportService, projectAccessRepository)
	adminPage(whiteListRepository, configRepository)
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
	return model
}