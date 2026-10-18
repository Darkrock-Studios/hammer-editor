package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.mfa.CodeResult
import com.darkrockstudios.apps.hammer.account.mfa.MfaRepository
import com.darkrockstudios.apps.hammer.account.mfa.lockMinutesRemaining
import com.darkrockstudios.apps.hammer.admin.AdminServerConfig
import com.darkrockstudios.apps.hammer.admin.ConfigRepository
import com.darkrockstudios.apps.hammer.admin.WhiteListRepository
import com.darkrockstudios.apps.hammer.frontend.data.PendingMfaSession
import com.darkrockstudios.apps.hammer.frontend.data.UserSession
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.monitoring.SecurityRepository
import com.darkrockstudios.apps.hammer.plugins.LOGIN_RATE_LIMIT
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import com.darkrockstudios.apps.hammer.plugin.NoticeSlot
import com.darkrockstudios.apps.hammer.plugin.activeAllowedUsersSource
import com.darkrockstudios.apps.hammer.plugin.putAllowedUsersNotice
import com.github.aymanizz.ktori18n.R
import com.github.aymanizz.ktori18n.t
import io.ktor.server.application.ApplicationCall
import io.ktor.server.mustache.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** How long a password-checked sign-in waits for its second factor. */
val PENDING_MFA_LIFETIME = 5.minutes

fun Route.authRoutes(
	accountsRepository: AccountsRepository,
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	securityRepository: SecurityRepository,
	mfaRepository: MfaRepository,
	clock: Clock,
) {
	loginPage(accountsRepository, whiteListRepository, configRepository, securityRepository, mfaRepository, clock)
	logout()
	unauthorized()
}

private fun Route.loginPage(
	accountsRepository: AccountsRepository,
	whiteListRepository: WhiteListRepository,
	configRepository: ConfigRepository,
	securityRepository: SecurityRepository,
	mfaRepository: MfaRepository,
	clock: Clock,
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

				val result = accountsRepository.verifyPassword(email = email, password = password)
				val session: UserSession?
				val credentialsError: String?
				if (!isSuccess(result)) {
					session = null
					credentialsError = result.displayMessageText(call) ?: "Login failed"
				} else if (mfaRepository.hasMfa(result.data.id)) {
					val candidate = UserSession(userId = result.data.id, username = email, isAdmin = result.data.is_admin)
					// The attempt is audited once the second factor settles it.
					if (sessionIsAuthorized(candidate, accountsRepository, whiteListRepository)) {
						val expires = clock.now() + PENDING_MFA_LIFETIME
						call.sessions.set(PendingMfaSession(result.data.id, expires.toEpochMilliseconds()))
						call.respondRedirect("/login/verify")
						return@post
					}
					session = null
					credentialsError = null
				} else {
					session = webSession(accountsRepository, result.data.id, email)
					credentialsError = null
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
					accountsRepository.issueToken(session.userId, "web")
					call.sessions.set(session)
					call.respondRedirect("/dashboard")
				} else {
					respondLoginPage(call, configRepository, credentialsError ?: allowedUsersRejection(call))
				}
			}
		}
	}

	route("/login/verify") {
		get {
			val pending = call.validPendingMfa(clock)
			if (pending == null) {
				call.respondRedirect("/login")
				return@get
			}
			val lockMessage = mfaRepository.lockedUntil(pending.userId)?.let {
				call.msg("login_verify_locked", lockMinutesRemaining(it, clock))
			}
			respondVerifyPage(call, lockMessage)
		}

		rateLimit(RateLimitName(LOGIN_RATE_LIMIT)) {
			post {
				val code = call.receiveParameters()["code"].orEmpty().filterNot { it.isWhitespace() }
				val pending = call.validPendingMfa(clock)
				val account = pending?.let { accountsRepository.getAccountOrNull(it.userId) }
				// A reset while the sign-in waited leaves nothing to verify; the password step decides again.
				if (pending == null || account == null || !mfaRepository.hasMfa(account.id)) {
					call.sessions.clear<PendingMfaSession>()
					respondLoginPage(call, configRepository, call.msg("login_verify_expired"))
					return@post
				}

				val result = mfaRepository.verifyCode(account.id, code)
				val session = if (result == CodeResult.Verified) webSession(accountsRepository, account.id, account.email) else null
				val authorized = session != null && sessionIsAuthorized(session, accountsRepository, whiteListRepository)
				securityRepository.recordLoginAttempt(
					email = account.email,
					ipAddress = call.request.origin.remoteAddress,
					success = authorized,
				)

				when {
					session != null && authorized -> {
						call.sessions.clear<PendingMfaSession>()
						accountsRepository.issueToken(session.userId, "web")
						call.sessions.set(session)
						call.respondRedirect("/dashboard")
					}

					session != null -> {
						call.sessions.clear<PendingMfaSession>()
						respondLoginPage(call, configRepository, allowedUsersRejection(call))
					}

					result is CodeResult.Locked ->
						respondVerifyPage(call, call.msg("login_verify_locked", lockMinutesRemaining(result.until, clock)))

					else -> respondVerifyPage(call, call.msg("login_verify_invalid"))
				}
			}
		}
	}
}

/** The pending sign-in, or null (clearing the cookie) once it has expired. */
private fun ApplicationCall.validPendingMfa(clock: Clock): PendingMfaSession? {
	val pending = sessions.get<PendingMfaSession>() ?: return null
	if (clock.now().toEpochMilliseconds() < pending.expires) return pending
	sessions.clear<PendingMfaSession>()
	return null
}

private suspend fun webSession(accountsRepository: AccountsRepository, userId: Long, email: String): UserSession =
	UserSession(userId = userId, username = email, isAdmin = accountsRepository.isAdmin(userId))

// Valid credentials without a whitelist entry get no session, because /dashboard would reject it
// and loop back to the login page.
private suspend fun allowedUsersRejection(call: ApplicationCall): String =
	call.activeAllowedUsersSource()?.rejectionMessage()?.text(call)
		?: call.t(R("api_allowedusers_rejected"))

private suspend fun respondLoginPage(call: ApplicationCall, configRepository: ConfigRepository, message: String) {
	val model = buildLoginModel(call, configRepository).toMutableMap()
	model["message"] = message
	call.respond(MustacheContent("login.mustache", call.withDefaults(model)))
}

private suspend fun respondVerifyPage(call: ApplicationCall, message: String?) {
	val recovery = call.request.queryParameters["method"] == "recovery"
	val model = buildMap {
		put("page_stylesheet", "/assets/css/login.css")
		put("useRecovery", recovery)
		message?.let { put("message", it) }
	}
	call.respond(MustacheContent("login-verify.mustache", call.withDefaults(model)))
}

private suspend fun buildLoginModel(
	call: ApplicationCall,
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