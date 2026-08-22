package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.monitoring.SecurityRepository
import com.darkrockstudios.apps.hammer.plugins.LOGIN_RATE_LIMIT
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.plugin.NoticeSlot
import com.darkrockstudios.apps.hammer.plugin.activeAllowedUsersSource
import com.darkrockstudios.apps.hammer.plugin.putAllowedUsersNotice
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.server.mustache.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.authRoutes(
	accountsRepository: AccountsRepository,
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	securityRepository: SecurityRepository,
) {
	loginPage(accountsRepository, whiteListRepository, configRepository, securityRepository)
	logout()
	unauthorized()
}

private fun Route.loginPage(
	accountsRepository: AccountsRepository,
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	securityRepository: SecurityRepository,
) {
	route("/login") {
		get {
			val session: UserSession? = call.sessions.get<UserSession>()
			if (session != null && sessionIsAuthorized(session, accountsRepository, whiteListRepository)) {
				call.respondRedirect("/dashboard")
			} else {
				// Drop a present-but-unauthorized cookie so the user isn't bounced
				// straight back to /dashboard and into a redirect loop.
				if (session != null) call.sessions.clear<UserSession>()
				val model = buildLoginModel(call, configRepository)
				call.respond(MustacheContent("login.mustache", call.withDefaults(model)))
			}
		}

		rateLimit(RateLimitName(LOGIN_RATE_LIMIT)) {
			post {
				val params = call.receiveParameters()
				val email = params["email"] ?: ""
				val password = params["password"] ?: ""

				val result = accountsRepository.login(
					email = email,
					password = password,
					installId = "web"
				)
				val session: UserSession?
				val credentialsError: String?
				if (isSuccess(result)) {
					val token = result.data
					session = UserSession(
						userId = token.userId,
						username = email,
						isAdmin = accountsRepository.isAdmin(token.userId)
					)
					credentialsError = null
				} else {
					session = null
					credentialsError = result.displayMessageText(call) ?: "Login failed"
				}

				// A whitelist rejection is a denied sign-in, so it is audited as a failure
				// to match what the API path records for the same credentials.
				val authorized = session != null &&
					sessionIsAuthorized(session, accountsRepository, whiteListRepository)

				securityRepository.recordLoginAttempt(
					email = email,
					ipAddress = call.request.origin.remoteAddress,
					success = authorized,
				)

				if (session != null && authorized) {
					call.sessions.set(session)
					call.respondRedirect("/dashboard")
				} else {
					val model = buildLoginModel(call, configRepository)
						.toMutableMap()
					// Valid credentials without a whitelist entry get no session, because
					// /dashboard would reject it and loop back here.
					model["message"] = credentialsError
						?: call.activeAllowedUsersSource()?.rejectionMessage()?.text(call)
						?: call.t(R("api_allowedusers_rejected"))
					call.respond(MustacheContent("login.mustache", call.withDefaults(model)))
				}
			}
		}
	}
}

private suspend fun buildLoginModel(
	call: RoutingCall,
	configRepository: ConfigRepository,
): Map<String, Any> {
	val contactEmail = configRepository.get(AdminServerConfig.CONTACT_EMAIL)

	return buildMap {
		put("page_stylesheet", "/assets/css/login.css")
		if (contactEmail.isNotBlank()) {
			put("contactEmail", contactEmail)
		}
		call.putAllowedUsersNotice(this, NoticeSlot.LOGIN)
	}
}

private fun Route.logout() {
	get("/logout") {
		call.sessions.clear<UserSession>()
		call.respondRedirect("/")
	}
}

private fun Route.unauthorized() {
	route("/unauthorized") {
		get {
			call.respond(MustacheContent("unauthorized.mustache", call.withDefaults(ERROR_PAGE_STYLE)))
		}
	}
}