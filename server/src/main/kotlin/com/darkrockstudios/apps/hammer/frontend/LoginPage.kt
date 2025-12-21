package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.authRoutes(
	accountsRepository: AccountsRepository,
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository
) {
	loginPage(accountsRepository, whiteListRepository, configRepository)
	logout()
	unauthorized()
}

private fun Route.loginPage(
	accountsRepository: AccountsRepository,
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository
) {
	route("/login") {
		get {
			val session: UserSession? = call.sessions.get<UserSession>()
			if (session != null) {
				call.respondRedirect("/dashboard")
			} else {
				val model = buildLoginModel(whiteListRepository, configRepository)
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
				call.sessions.set(
					UserSession(
						userId = token.userId,
						username = email,
						isAdmin = isAdmin
					)
				)
				call.respondRedirect("/dashboard")
			} else {
				val message = result.displayMessageText(call) ?: "Login failed"
				val model = buildLoginModel(whiteListRepository, configRepository).toMutableMap()
				model["message"] = message
				call.respond(MustacheContent("login.mustache", call.withDefaults(model)))
			}
		}
	}
}

private suspend fun buildLoginModel(
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository
): Map<String, Any> {
	val useWhiteList = whiteListRepository.useWhiteList()
	val contactEmail = configRepository.get(AdminServerConfig.CONTACT_EMAIL)

	return buildMap {
		put("page_stylesheet", "/assets/css/login.css")
		put("whitelistEnabled", useWhiteList)
		if (contactEmail.isNotBlank()) {
			put("contactEmail", contactEmail)
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
			call.respond(MustacheContent("unauthorized.mustache", call.withDefaults()))
		}
	}
}