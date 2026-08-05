package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.ServerConfig
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.plugins.LOGIN_RATE_LIMIT
import com.darkrockstudios.apps.hammer.projects.ProjectsRepository
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.ktor.server.mustache.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.inject

fun Route.setupPage(serverConfig: ServerConfig) {
	val accountsRepository: AccountsRepository by inject()
	val projectsRepository: ProjectsRepository by inject()

	route("/setup") {
		get {
			// If users already exist, redirect to home
			if (accountsRepository.hasUsers()) {
				call.respondRedirect("/")
				return@get
			}

			call.respond(MustacheContent("setup.mustache", buildSetupModel(call, serverConfig)))
		}

		rateLimit(RateLimitName(LOGIN_RATE_LIMIT)) {
			post {
				// Guards against races with app-side account creation and double-submits
				if (accountsRepository.hasUsers()) {
					call.respondRedirect("/login")
					return@post
				}

				val params = call.receiveParameters()
				val email = params["email"]?.trim() ?: ""
				val password = params["password"] ?: ""
				val confirmPassword = params["confirmPassword"] ?: ""

				suspend fun respondWithError(message: String) {
					val model = buildSetupModel(call, serverConfig)
					model["error"] = message
					model["email"] = email
					call.respond(MustacheContent("setup.mustache", model))
				}

				if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
					respondWithError(call.msg("setup_error_fields_required"))
					return@post
				}
				if (password != confirmPassword) {
					respondWithError(call.msg("setup_error_password_mismatch"))
					return@post
				}

				// ToS challenge is intentionally skipped for the first admin account,
				// so this goes to the repository directly rather than AccountsComponent.
				val result = accountsRepository.createAccount(
					email = email,
					installId = "web",
					password = password,
				)
				if (isSuccess(result)) {
					val token = result.data
					projectsRepository.createUserData(token.userId)

					// Read from the DB rather than assuming admin: a lost creation race
					// means this account wasn't first and must not get an admin cookie.
					val isAdmin = accountsRepository.isAdmin(token.userId)
					val session = UserSession(
						userId = token.userId,
						username = email,
						isAdmin = isAdmin,
					)
					call.sessions.set(session)
					call.respondRedirect(if (isAdmin) "/admin" else "/dashboard")
				} else {
					respondWithError(result.displayMessageText(call) ?: call.msg("setup_error_generic"))
				}
			}
		}
	}
}

private suspend fun buildSetupModel(call: RoutingCall, serverConfig: ServerConfig): MutableMap<String, Any> {
	val model = call.withDefaults()
	model["page_stylesheet"] = "/assets/css/error.css"
	model["serverAddress"] = buildServerAddress(serverConfig)
	return model
}

private fun buildServerAddress(config: ServerConfig): String {
	val host = config.host
	val port = config.port
	val sslPort = config.sslPort
	val hasSsl = config.sslCert != null

	return if (hasSsl) {
		if (sslPort == 443) {
			"https://$host"
		} else {
			"https://$host:$sslPort"
		}
	} else {
		if (port == 80) {
			"http://$host"
		} else {
			"http://$host:$port"
		}
	}
}
