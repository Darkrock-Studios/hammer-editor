package com.darkrockstudios.apps.hammer.email

interface EmailService {
	suspend fun sendEmail(
		to: String,
		subject: String,
		bodyHtml: String,
		bodyText: String? = null
	): EmailResult

	suspend fun isConfigured(): Boolean
}

sealed class EmailResult {
	data object Success : EmailResult()
	data class Failure(val reason: String, val exception: Throwable? = null) : EmailResult()
}
