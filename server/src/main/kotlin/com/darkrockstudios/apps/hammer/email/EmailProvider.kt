package com.darkrockstudios.apps.hammer.email

import kotlinx.serialization.Serializable

@Serializable
enum class EmailProvider {
	SMTP,
	SENDGRID,
	POSTMARK,
	MAILGUN,
}
