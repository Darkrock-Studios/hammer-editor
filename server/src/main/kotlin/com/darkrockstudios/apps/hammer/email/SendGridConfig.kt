package com.darkrockstudios.apps.hammer.email

import kotlinx.serialization.Serializable

@Serializable
data class SendGridConfig(
	val apiKey: String = "",
	val fromAddress: String = "",
	val fromName: String = "Hammer Server",
)
