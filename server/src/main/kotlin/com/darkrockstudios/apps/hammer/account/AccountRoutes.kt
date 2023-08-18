package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.INVALID_USER_ID
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

fun Route.accountRoutes() {
	route("/account") {
		createAccount()
		login()
		refreshToken()
		authenticate(USER_AUTH) {
			testAuth()
		}
	}
}

private fun Route.createAccount() {
	val accountsComponent: AccountsComponent = get()

	post("/create") {
		val formParameters = call.receiveParameters()
		val email = formParameters["email"].toString()
		val password = formParameters["password"].toString()
		val installId = formParameters["installId"].toString()

		val result = accountsComponent.createAccount(email = email, installId = installId, password = password)
		if (isSuccess(result)) {
			val token = result.data
			call.respond(HttpStatusCode.Created, token)
		} else {
			val response = HttpResponseError(
				error = "Failed to create account",
				displayMessage = result.displayMessageText(call, R("api.error.unknown"))
			)
			call.respond(status = HttpStatusCode.Conflict, response)
		}
	}
}

private fun Route.login() {
	val accountsComponent: AccountsComponent = get()

	post("/login") {
		val formParameters = call.receiveParameters()
		val email = formParameters["email"].toString()
		val password = formParameters["password"].toString()
		val installId = formParameters["installId"].toString()

		val result = accountsComponent.login(email = email, password = password, installId = installId)
		if (isSuccess(result)) {
			val authToken = result.data
			call.respond(authToken)
		} else {
			val response = HttpResponseError(
				error = "Failed to authenticate",
				displayMessage = result.displayMessageText(call, R("api.error.unknown"))
			)
			call.respond(status = HttpStatusCode.Unauthorized, response)
		}
	}
}

private fun Route.refreshToken() {
	val accountsComponent: AccountsComponent = get()

	post("/refresh_token/{userId}") {
		val userId = call.parameters["userId"]?.toLongOrNull() ?: INVALID_USER_ID

		val formParameters = call.receiveParameters()
		val installId = formParameters["installId"].toString()
		val refreshToken = formParameters["refreshToken"].toString()

		val result =
			accountsComponent.refreshToken(userId = userId, installId = installId, refreshToken = refreshToken)
		if (isSuccess(result)) {
			val token = result.data
			call.respond(token)
		} else {
			call.respond(
				status = HttpStatusCode.Unauthorized,
				HttpResponseError(
					error = "Unauthorized",
					displayMessage = call.t(R("api.accounts.tokenrefresh.error"))
				)
			)
		}
	}
}

private fun Route.testAuth() {
	get("/test_auth/{userId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		call.respondText(call.t(R("api.accounts.testauth.error"), principal.id))
	}
}
