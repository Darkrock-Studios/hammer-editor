package com.darkrockstudios.apps.hammer.frontend

import com.darkrockstudios.apps.hammer.account.PasswordResetRepository
import com.darkrockstudios.apps.hammer.account.TokenValidationResult
import com.darkrockstudios.apps.hammer.frontend.utils.msg
import com.darkrockstudios.apps.hammer.frontend.utils.publicBaseUrl
import com.darkrockstudios.apps.hammer.utilities.isSuccess
import io.ktor.server.mustache.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.passwordResetRoutes(passwordResetRepository: PasswordResetRepository) {
	forgotPasswordPage(passwordResetRepository)
	resetPasswordPage(passwordResetRepository)
}

// GET /forgot-password - Display forgot password form
private fun Route.forgotPasswordPage(passwordResetRepository: PasswordResetRepository) {
	route("/forgot-password") {
		get {
			val model = mapOf(
				"page_stylesheet" to "/assets/css/login.css"
			)
			call.respond(MustacheContent("forgot-password.mustache", call.withDefaults(model)))
		}

		post {
			val params = call.receiveParameters()
			val email = params["email"]?.trim() ?: ""

			if (email.isEmpty()) {
				val model = mapOf(
					"page_stylesheet" to "/assets/css/login.css",
					"error" to call.msg("password_reset_error_email_required")
				)
				call.respond(MustacheContent("forgot-password.mustache", call.withDefaults(model)))
				return@post
			}

			// Request password reset (always returns success to prevent enumeration)
			passwordResetRepository.requestPasswordReset(email) { token ->
				"${call.publicBaseUrl()}/reset-password?token=$token"
			}

			// Show success message
			val model = mapOf(
				"page_stylesheet" to "/assets/css/login.css",
				"success" to true,
				"email" to email
			)
			call.respond(MustacheContent("forgot-password.mustache", call.withDefaults(model)))
		}
	}
}

// GET /reset-password - Display password reset form
private fun Route.resetPasswordPage(passwordResetRepository: PasswordResetRepository) {
	route("/reset-password") {
		get {
			val token = call.request.queryParameters["token"] ?: ""

			if (token.isEmpty()) {
				call.respondRedirect("/forgot-password")
				return@get
			}

			// Validate token
			val validationResult = passwordResetRepository.validateResetToken(token)

			when (validationResult) {
				is TokenValidationResult.Valid -> {
					val model = mapOf(
						"page_stylesheet" to "/assets/css/login.css",
						"token" to token
					)
					call.respond(MustacheContent("reset-password.mustache", call.withDefaults(model)))
				}

				is TokenValidationResult.Expired -> {
					val model = mapOf(
						"page_stylesheet" to "/assets/css/login.css",
						"error" to call.msg("password_reset_error_expired")
					)
					call.respond(MustacheContent("reset-password-error.mustache", call.withDefaults(model)))
				}

				is TokenValidationResult.AlreadyUsed -> {
					val model = mapOf(
						"page_stylesheet" to "/assets/css/login.css",
						"error" to call.msg("password_reset_error_already_used")
					)
					call.respond(MustacheContent("reset-password-error.mustache", call.withDefaults(model)))
				}

				is TokenValidationResult.Invalid -> {
					val model = mapOf(
						"page_stylesheet" to "/assets/css/login.css",
						"error" to call.msg("password_reset_error_invalid_token")
					)
					call.respond(MustacheContent("reset-password-error.mustache", call.withDefaults(model)))
				}
			}
		}

		post {
			val params = call.receiveParameters()
			val token = params["token"] ?: ""
			val password = params["password"] ?: ""
			val confirmPassword = params["confirmPassword"] ?: ""

			// Validate inputs
			if (password.isEmpty() || confirmPassword.isEmpty()) {
				val model = mapOf(
					"page_stylesheet" to "/assets/css/login.css",
					"token" to token,
					"error" to call.msg("password_reset_error_fields_required")
				)
				call.respond(MustacheContent("reset-password.mustache", call.withDefaults(model)))
				return@post
			}

			if (password != confirmPassword) {
				val model = mapOf(
					"page_stylesheet" to "/assets/css/login.css",
					"token" to token,
					"error" to call.msg("password_reset_error_mismatch")
				)
				call.respond(MustacheContent("reset-password.mustache", call.withDefaults(model)))
				return@post
			}

			// Attempt to reset password
			val result = passwordResetRepository.resetPassword(token, password)

			if (isSuccess(result)) {
				val model = mapOf(
					"page_stylesheet" to "/assets/css/login.css",
					"success" to true
				)
				call.respond(MustacheContent("reset-password.mustache", call.withDefaults(model)))
			} else {
				val errorMessage = result.displayMessageText(call) ?: call.msg("password_reset_error_generic")
				val model = mapOf(
					"page_stylesheet" to "/assets/css/login.css",
					"token" to token,
					"error" to errorMessage
				)
				call.respond(MustacheContent("reset-password.mustache", call.withDefaults(model)))
			}
		}
	}
}
