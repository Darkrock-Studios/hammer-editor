package com.darkrockstudios.apps.hammer.account

import com.darkrockstudios.apps.hammer.base.http.ApiErrorCode
import com.darkrockstudios.apps.hammer.base.http.HTTP_STATUS_TERMS_OF_SERVICE
import com.darkrockstudios.apps.hammer.base.http.HttpResponseError
import com.darkrockstudios.apps.hammer.base.http.INVALID_USER_ID
import com.darkrockstudios.apps.hammer.monitoring.SecurityRepository
import com.darkrockstudios.apps.hammer.plugins.LOGIN_RATE_LIMIT
import com.darkrockstudios.apps.hammer.plugins.ServerUserIdPrincipal
import com.darkrockstudios.apps.hammer.plugins.USER_AUTH
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

fun Route.accountRoutes() {
	route("/account") {
		rateLimit(RateLimitName(LOGIN_RATE_LIMIT)) {
			createAccount()
			login()
		}
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
		val acceptedTosVersion = formParameters["acceptedTosVersion"]

		val result = accountsComponent.createAccount(
			email = email,
			installId = installId,
			password = password,
			acceptedTosVersion = acceptedTosVersion,
		)
		when (result) {
			is CreateAccountResult.Success -> call.respond(HttpStatusCode.Created, result.token)
			is CreateAccountResult.TermsRequired -> call.respond(
				status = HttpStatusCode(HTTP_STATUS_TERMS_OF_SERVICE, "Unavailable For Legal Reasons"),
				message = result.challenge,
			)

			is CreateAccountResult.Failure -> {
				val response = HttpResponseError(
					error = "Failed to create account",
					displayMessage = result.failure.displayMessageText(call, R("api_error_unknown")),
					errorCode = createErrorCode(result.failure.exception),
				)
				call.respond(status = createErrorStatus(result.failure.exception), response)
			}
		}
	}
}

private fun Route.login() {
	val accountsComponent: AccountsComponent = get()
	val securityRepository: SecurityRepository = get()

	post("/login") {
		val formParameters = call.receiveParameters()
		val email = formParameters["email"].toString()
		val password = formParameters["password"].toString()
		val installId = formParameters["installId"].toString()

		val result = accountsComponent.login(email = email, password = password, installId = installId)

		securityRepository.recordLoginAttempt(
			email = email,
			ipAddress = call.request.origin.remoteAddress,
			success = isSuccess(result),
		)

		if (isSuccess(result)) {
			val authToken = result.data
			call.respond(authToken)
		} else {
			// A rejected whitelist isn't a credential problem, and answering 401 made it
			// indistinguishable from a wrong password in both the logs and the client.
			val notWhitelisted = result.exception is NotWhitelisted
			val response = HttpResponseError(
				error = "Failed to authenticate",
				displayMessage = result.displayMessageText(call, R("api_error_unknown")),
				errorCode = if (notWhitelisted) ApiErrorCode.NOT_WHITELISTED
				else ApiErrorCode.INVALID_CREDENTIALS,
			)
			val status = if (notWhitelisted) HttpStatusCode.Forbidden else HttpStatusCode.Unauthorized
			call.respond(status = status, message = response)
		}
	}
}

private fun createErrorStatus(cause: Throwable?): HttpStatusCode = when (cause) {
	is AccountAlreadyExists, is AccountPendingDeletion -> HttpStatusCode.Conflict
	is InvalidEmail, is InvalidPassword -> HttpStatusCode.BadRequest
	is NotWhitelisted -> HttpStatusCode.Forbidden
	else -> HttpStatusCode.Conflict
}

private fun createErrorCode(cause: Throwable?): String? = when (cause) {
	is AccountAlreadyExists -> ApiErrorCode.ACCOUNT_EXISTS
	is AccountPendingDeletion -> ApiErrorCode.ACCOUNT_PENDING_DELETION
	is InvalidEmail -> ApiErrorCode.INVALID_EMAIL
	is InvalidPassword -> InvalidPassword.getCode(cause.result)
	is NotWhitelisted -> ApiErrorCode.NOT_WHITELISTED
	else -> null
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
					displayMessage = result.displayMessageText(call, R("api_accounts_tokenrefresh_error")),
					errorCode = ApiErrorCode.TOKEN_INVALID,
				)
			)
		}
	}
}

private fun Route.testAuth() {
	get("/test_auth/{userId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		call.respondText(call.t(R("api_accounts_testauth_error"), principal.id))
	}
}
