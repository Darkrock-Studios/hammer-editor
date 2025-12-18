package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.withMessages
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.ktor.server.mustache.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Route.authRoutes(accountsRepository: AccountsRepository) {
	login(accountsRepository)
	logout()
}

private fun Route.login(accountsRepository: AccountsRepository) {
	route("/login") {
		get {
			val session: UserSession? = call.sessions.get<UserSession>()
			if (session != null) {
				call.respondRedirect("/admin")
			} else {
				call.respond(MustacheContent("login.mustache", call.withMessages()))
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
				call.respondRedirect("/admin")
			} else {
				val message = result.displayMessageText(call) ?: "Login failed"
				val model = mapOf(
					"message" to message
				)
				call.respond(MustacheContent("login.mustache", call.withMessages(model)))
			}
		}
	}
}

private fun Route.logout() {
	get("/logout") {
		call.sessions.clear<UserSession>()
		call.respondRedirect("/")
	}
}