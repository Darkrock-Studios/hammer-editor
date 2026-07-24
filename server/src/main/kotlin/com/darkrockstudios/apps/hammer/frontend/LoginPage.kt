package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.authRoutes(
	accountsRepository: AccountsRepository,
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	serverConfig: ServerConfig
) {
	loginPage(accountsRepository, whiteListRepository, configRepository, serverConfig)
	logout()
	unauthorized()
}

private fun Route.loginPage(
	accountsRepository: AccountsRepository,
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	serverConfig: ServerConfig
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
				val model = buildLoginModel(call, whiteListRepository, configRepository, serverConfig)
				call.respond(MustacheContent("login.mustache", call.withDefaults(model)))
			}
		}

		post {
			val params = call.receiveParameters()
			val email = params["email"] ?: ""
			val password = params["password"] ?: ""

			val result = accountsRepository.login(
				email = email,
				password = password,
				installId = "web"
			)
			if (isSuccess(result)) {
				val token = result.data
				val isAdmin = accountsRepository.isAdmin(token.userId)
				val session = UserSession(
					userId = token.userId,
					username = email,
					isAdmin = isAdmin
				)
				if (sessionIsAuthorized(session, accountsRepository, whiteListRepository)) {
					call.sessions.set(session)
					call.respondRedirect("/dashboard")
				} else {
					// Credentials are valid but the user isn't whitelisted; don't set a
					// session that /dashboard would reject (which would loop back here).
					val model = buildLoginModel(call, whiteListRepository, configRepository, serverConfig)
						.toMutableMap()
					model["message"] = call.t(R("api_whitelist_rejected"))
					call.respond(MustacheContent("login.mustache", call.withDefaults(model)))
				}
			} else {
				val message = result.displayMessageText(call) ?: "Login failed"
				val model = buildLoginModel(call, whiteListRepository, configRepository, serverConfig).toMutableMap()
				model["message"] = message
				call.respond(MustacheContent("login.mustache", call.withDefaults(model)))
			}
		}
	}
}

private suspend fun buildLoginModel(
	call: RoutingCall,
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	serverConfig: ServerConfig
): Map<String, Any> {
	val useWhiteList = whiteListRepository.useWhiteList()
	val contactEmail = configRepository.get(AdminServerConfig.CONTACT_EMAIL)
	val patreonConfig = configRepository.get(AdminServerConfig.PATREON_CONFIG)
	val patreonFeatureEnabled = serverConfig.patreonEnabled == true
	val patreonActive = patreonFeatureEnabled && patreonConfig.enabled && patreonConfig.patreonUrl.isNotBlank()

	return buildMap {
		put("page_stylesheet", "/assets/css/login.css")
		put("whitelistEnabled", useWhiteList)
		if (contactEmail.isNotBlank()) {
			put("contactEmail", contactEmail)
		}
		if (patreonActive) {
			val amount = "%.2f".format(patreonConfig.minimumAmountCents / 100.0)
			put("patreonEnabled", true)
			put("patreonUrl", patreonConfig.patreonUrl)
			put("patreonMessage", call.t(R("login_patreon_notice_message"), amount))
		}
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