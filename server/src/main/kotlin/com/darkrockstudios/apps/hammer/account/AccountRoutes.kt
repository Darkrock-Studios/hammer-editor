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
import io.ktor.http.auth.HttpAuthHeader
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
			loginMfa()
			authenticate(USER_AUTH) {
				pairInstall()
			}
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
		val code = formParameters["code"]?.takeIf { it.isNotBlank() }

		val result = accountsComponent.login(email = email, password = password, installId = installId, code = code)

		// A correct password awaiting its second factor is neither a success nor a failure yet.
		if (result !is LoginResult.MfaRequired) {
			securityRepository.recordLoginAttempt(
				email = email,
				ipAddress = call.request.origin.remoteAddress,
				success = result is LoginResult.Success,
			)
		}

		respondLoginResult(result)
	}
}

private fun Route.loginMfa() {
	val accountsComponent: AccountsComponent = get()
	val accountsRepository: AccountsRepository = get()
	val securityRepository: SecurityRepository = get()

	post("/login/mfa") {
		val formParameters = call.receiveParameters()
		val mfaToken = formParameters["mfaToken"]
		val code = formParameters["code"]
		val installId = formParameters["installId"]
		if (mfaToken.isNullOrBlank() || code.isNullOrBlank() || installId.isNullOrBlank()) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				message = HttpResponseError(
					error = "Missing mfaToken, code, or installId",
					displayMessage = call.t(R("api_accounts_login_mfa_invalid")),
					errorCode = ApiErrorCode.MFA_CODE_INVALID,
				),
			)
			return@post
		}

		val result = accountsComponent.loginMfa(mfaToken = mfaToken, code = code, installId = installId)

		val email = when (result) {
			is LoginResult.Success -> accountsRepository.getAccountOrNull(result.token.userId)?.email
			is LoginResult.MfaCodeInvalid -> result.email
			is LoginResult.Failure, is LoginResult.MfaRequired -> null
		}
		securityRepository.recordLoginAttempt(
			email = email,
			ipAddress = call.request.origin.remoteAddress,
			success = result is LoginResult.Success,
		)

		respondLoginResult(result)
	}
}

private suspend fun RoutingContext.respondLoginResult(result: LoginResult) {
	when (result) {
		is LoginResult.Success -> call.respond(result.token)
		is LoginResult.MfaRequired -> call.respond(
			status = HttpStatusCode.Unauthorized,
			message = HttpResponseError(
				error = "Second factor required",
				displayMessage = call.t(R("api_accounts_login_mfa_required")),
				errorCode = ApiErrorCode.MFA_REQUIRED,
				mfa = result.challenge,
			),
		)

		is LoginResult.MfaCodeInvalid -> call.respond(
			status = HttpStatusCode.Unauthorized,
			message = HttpResponseError(
				error = "Invalid second factor",
				displayMessage = call.t(R("api_accounts_login_mfa_invalid")),
				errorCode = ApiErrorCode.MFA_CODE_INVALID,
			),
		)

		is LoginResult.Failure -> {
			// A rejected whitelist isn't a credential problem, and answering 401 made it
			// indistinguishable from a wrong password in both the logs and the client.
			val notWhitelisted = result.failure.exception is NotWhitelisted
			val response = HttpResponseError(
				error = "Failed to authenticate",
				displayMessage = result.failure.displayMessageText(call, R("api_error_unknown")),
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

private fun Route.pairInstall() {
	val accountsComponent: AccountsComponent = get()
	val accountsRepository: AccountsRepository = get()

	post("/pair_install/{userId}") {
		val principal = call.principal<ServerUserIdPrincipal>()!!
		val newInstallId = call.receiveParameters()["installId"]

		// Derived from the authenticated token (not client-asserted). Parsed the way Ktor itself
		// parses it, because the auth scheme is case insensitive.
		val callerInstallId = (call.request.parseAuthorizationHeader() as? HttpAuthHeader.Single)
			?.blob
			?.takeIf { it.isNotBlank() }
			?.let { accountsRepository.getInstallId(it) }

		if (newInstallId == null || callerInstallId == null) {
			call.respond(
				status = HttpStatusCode.BadRequest,
				HttpResponseError(
					error = "Missing install id",
					displayMessage = call.t(R("api_accounts_pair_error_invalidinstall")),
				)
			)
			return@post
		}

		val result = accountsComponent.pairInstall(
			userId = principal.id,
			callerInstallId = callerInstallId,
			newInstallId = newInstallId,
		)
		if (isSuccess(result)) {
			call.application.environment.log.info("Paired a new install for user ${principal.id}")
			call.respond(HttpStatusCode.Created, result.data)
		} else {
			val status = when (result.exception) {
				is InvalidInstallId -> HttpStatusCode.BadRequest
				is NotWhitelisted -> HttpStatusCode.Forbidden
				else -> HttpStatusCode.Unauthorized
			}
			val errorCode = when (result.exception) {
				is InvalidInstallId -> null
				is NotWhitelisted -> ApiErrorCode.NOT_WHITELISTED
				else -> ApiErrorCode.TOKEN_INVALID
			}
			call.respond(
				status = status,
				HttpResponseError(
					error = "Failed to pair install",
					displayMessage = result.displayMessageText(call, R("api_accounts_pair_error")),
					errorCode = errorCode,
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
