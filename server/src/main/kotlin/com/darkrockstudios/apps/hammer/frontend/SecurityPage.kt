package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.AccountsRepository
import com.darkrockstudios.apps.hammer.account.mfa.CodeResult
import com.darkrockstudios.apps.hammer.account.mfa.MfaRepository
import com.darkrockstudios.apps.hammer.account.mfa.lockMinutesRemaining
import com.darkrockstudios.apps.hammer.frontend.utils.Toast
import com.darkrockstudios.apps.hammer.frontend.utils.authenticatedOnly
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.qrCodeSvg
import com.darkrockstudios.apps.hammer.frontend.utils.requireUser
import com.darkrockstudios.apps.hammer.frontend.utils.respondTemplateWithToast
import com.darkrockstudios.apps.hammer.frontend.utils.respondToast
import com.darkrockstudios.apps.hammer.plugins.LOGIN_RATE_LIMIT
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.htmx.hx
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.sessions
import java.net.URLEncoder
import kotlin.time.Clock

private const val FACTORS_TEMPLATE = "partials/security-factors.mustache"

fun Route.securityPage(
	accountsRepository: AccountsRepository,
	mfaRepository: MfaRepository,
	clock: Clock,
) {
	authenticatedOnly {
		route("/dashboard/security") {
			get {
				val userId = call.sessions.requireUser().userId
				val model = factorsModel(call, mfaRepository, userId)
				model["page_stylesheet"] = "/assets/css/security.css"
				model["page_script"] = "/assets/js/security.js"
				model["title"] = call.msg("security_page_title")
				call.respond(MustacheContent("security.mustache", model))
			}

			hx.get("/factors") {
				val userId = call.sessions.requireUser().userId
				call.respond(MustacheContent(FACTORS_TEMPLATE, factorsModel(call, mfaRepository, userId)))
			}

			hx.post("/totp/begin") {
				val userId = call.sessions.requireUser().userId
				val enrollment = mfaRepository.beginTotp(userId)
				if (enrollment == null) {
					respondToast(call.msg("security_totp_already_enabled"), Toast.Error, HttpStatusCode.BadRequest)
					return@post
				}

				val model = call.withDefaults(
					mapOf(
						"qrSvg" to qrCodeSvg(enrollment.otpauthUri),
						"otpauthUri" to enrollment.otpauthUri,
						"secretGrouped" to enrollment.secret.chunked(4).joinToString(" "),
					)
				)
				call.respond(MustacheContent("partials/security-totp-setup.mustache", model))
			}

			hx.post("/totp/confirm") {
				val userId = call.sessions.requireUser().userId
				val code = call.receiveParameters()["code"].orEmpty().filterNot { it.isWhitespace() }
				val codes = mfaRepository.confirmTotp(userId, code)
				if (codes == null) {
					respondToast(call.msg("security_totp_confirm_invalid"), Toast.Error, HttpStatusCode.BadRequest)
					return@post
				}

				respondFactors(mfaRepository, accountsRepository, userId, "security_totp_toast_enabled", codes)
			}

			// Each takes the account password, so they share the login limiter.
			rateLimit(RateLimitName(LOGIN_RATE_LIMIT)) {
				hx.post("/totp/disable") {
					val userId = call.sessions.requireUser().userId
					val params = call.receiveParameters()
					if (!passwordMatches(accountsRepository, userId, params["password"])) return@post

					when (val result = mfaRepository.verifyCode(userId, params["code"].orEmpty().filterNot { it.isWhitespace() })) {
						CodeResult.Verified -> {
							mfaRepository.disableTotp(userId)
							respondFactors(mfaRepository, accountsRepository, userId, "security_totp_toast_disabled")
						}

						CodeResult.Invalid ->
							respondToast(call.msg("security_error_code"), Toast.Error, HttpStatusCode.BadRequest)

						is CodeResult.Locked -> respondToast(
							call.msg("security_error_locked", lockMinutesRemaining(result.until, clock)),
							Toast.Error,
							HttpStatusCode.BadRequest,
						)
					}
				}

				hx.post("/recovery-codes/regenerate") {
					val userId = call.sessions.requireUser().userId
					if (!passwordMatches(accountsRepository, userId, call.receiveParameters()["password"])) {
						return@post
					}
					if (!mfaRepository.hasMfa(userId)) {
						respondToast(call.msg("security_error_no_totp"), Toast.Error, HttpStatusCode.BadRequest)
						return@post
					}

					val codes = mfaRepository.regenerateRecoveryCodes(userId)
					respondFactors(mfaRepository, accountsRepository, userId, "security_recovery_toast_regenerated", codes)
				}
			}
		}
	}
}

/** Answers with an error toast and returns false when [password] is not the account's. */
private suspend fun RoutingContext.passwordMatches(
	accountsRepository: AccountsRepository,
	userId: Long,
	password: String?,
): Boolean {
	val email = accountsRepository.getAccount(userId).email
	if (password != null && isSuccess(accountsRepository.verifyPassword(email, password))) return true
	respondToast(call.msg("security_error_password"), Toast.Error, HttpStatusCode.BadRequest)
	return false
}

private suspend fun RoutingContext.respondFactors(
	mfaRepository: MfaRepository,
	accountsRepository: AccountsRepository,
	userId: Long,
	toastKey: String,
	freshCodes: List<String>? = null,
) {
	val model = factorsModel(call, mfaRepository, userId)
	if (freshCodes != null) {
		val email = accountsRepository.getAccount(userId).email
		model["hasFreshCodes"] = true
		model["freshCodes"] = freshCodes
		model["recoveryDownload"] = recoveryCodesDownload(call.msg("security_recovery_file_heading", email), freshCodes)
	}
	respondTemplateWithToast(FACTORS_TEMPLATE, model, call.msg(toastKey), Toast.Success)
}

private suspend fun factorsModel(call: ApplicationCall, mfaRepository: MfaRepository, userId: Long): MutableMap<String, Any> {
	val enabled = mfaRepository.hasMfa(userId)
	val model = call.withDefaults(mapOf("totpEnabled" to enabled))
	if (enabled) {
		model["recoveryRemaining"] = call.msg(
			"security_recovery_remaining",
			mfaRepository.unusedRecoveryCodes(userId),
			MfaRepository.RECOVERY_CODE_COUNT,
		)
	}
	return model
}

/** A data URI, so the codes never need to be held server-side for a second request. */
private fun recoveryCodesDownload(heading: String, codes: List<String>): String {
	val text = (listOf(heading, "") + codes).joinToString("\n", postfix = "\n")
	return "data:text/plain;charset=utf-8," + URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
}
