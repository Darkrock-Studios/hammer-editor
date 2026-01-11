package com.darkrockstudios.apps.hammer.email

import kotlinx.serialization.Serializable

@Serializable
data class SmtpConfig(
	val host: String = "",
	val port: Int = 587,
	val username: String = "",
	val password: String = "",
	val fromAddress: String = "",
	val fromName: String = "Hammer Server",
	val useTls: Boolean = true,
	val useStartTls: Boolean = true,
)
