package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsComponent
import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.CreateAccountResult
import com.darkrockstudios.apps.hammer.account.TermsOfServiceRepository
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.monitoring.SecurityRepository
import com.darkrockstudios.apps.hammer.plugins.LOGIN_RATE_LIMIT
import io.ktor.server.mustache.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

/**
 * Public self-registration. Goes through [AccountsComponent.createAccount] so the
 * allowed users list and the Terms of Service challenge are enforced; only emails
 * the admin has allowed can actually create an account here.
 */
fun Route.signupPage(
	accountsComponent: AccountsComponent,
	accountsRepository: AccountsRepository,
	whiteListRepository: WhiteListRepository,
	termsOfServiceRepository: TermsOfServiceRepository,
	configRepository: ConfigRepository,
	securityRepository: SecurityRepository,
) {
	route("/signup") {
		get {
			val session: UserSession? = call.sessions.get<UserSession>()
			if (session != null && sessionIsAuthorized(session, accountsRepository, whiteListRepository)) {
				call.respondRedirect("/dashboard")
			} else {
				if (session != null) call.sessions.clear<UserSession>()
				val model = buildSignupModel(call, termsOfServiceRepository, configRepository)
				call.respond(MustacheContent("signup.mustache", model))
			}
		}

		rateLimit(RateLimitName(LOGIN_RATE_LIMIT)) {
			post {
				val params = call.receiveParameters()
				val email = params["email"]?.trim() ?: ""
				val password = params["password"] ?: ""
				val confirmPassword = params["confirmPassword"] ?: ""
				val tosAccepted = params["tosAccepted"] != null
				val acceptedTosVersion = params["acceptedTosVersion"]

				suspend fun respondWithError(message: String) {
					val model = buildSignupModel(call, termsOfServiceRepository, configRepository)
					model["error"] = message
					model["email"] = email
					call.respond(MustacheContent("signup.mustache", model))
				}

				if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
					respondWithError(call.msg("signup_error_fields_required"))
					return@post
				}
				if (password != confirmPassword) {
					respondWithError(call.msg("signup_error_password_mismatch"))
					return@post
				}
				// The component re-checks the version; this gate just keeps the form
				// honest so an unchecked box never reaches account creation.
				if (termsOfServiceRepository.challenge() != null && !tosAccepted) {
					respondWithError(call.msg("signup_error_tos_required"))
					return@post
				}

				val result = accountsComponent.createAccount(
					email = email,
					installId = "web",
					password = password,
					acceptedTosVersion = acceptedTosVersion,
				)
				when (result) {
					is CreateAccountResult.Success -> {
						securityRepository.recordLoginAttempt(
							email = email,
							ipAddress = call.request.origin.remoteAddress,
							success = true,
						)
						val token = result.token
						val session = UserSession(
							userId = token.userId,
							username = email,
							isAdmin = accountsRepository.isAdmin(token.userId),
						)
						call.sessions.set(session)
						call.respondRedirect("/dashboard")
					}

					is CreateAccountResult.TermsRequired -> {
						// The ToS changed between render and submit; the re-render
						// carries the new version in its hidden field.
						respondWithError(call.msg("signup_error_tos_required"))
					}

					is CreateAccountResult.Failure -> {
						// A not-allowed email on a public form is an access probe and
						// is audited like a failed login; validation failures are not.
						if (accountsComponent.checkIfWhiteListRejected(email)) {
							securityRepository.recordLoginAttempt(
								email = email,
								ipAddress = call.request.origin.remoteAddress,
								success = false,
							)
						}
						respondWithError(
							result.failure.displayMessageText(call) ?: call.msg("signup_error_generic")
						)
					}
				}
			}
		}
	}
}

private suspend fun buildSignupModel(
	call: RoutingCall,
	termsOfServiceRepository: TermsOfServiceRepository,
	configRepository: ConfigRepository,
): MutableMap<String, Any> {
	val model = call.withDefaults()
	model["page_stylesheet"] = "/assets/css/login.css"
	termsOfServiceRepository.challenge()?.let { model["tosVersion"] = it.version }
	val contactEmail = configRepository.get(AdminServerConfig.CONTACT_EMAIL)
	if (contactEmail.isNotBlank()) {
		model["contactEmail"] = contactEmail
	}
	return model
}
