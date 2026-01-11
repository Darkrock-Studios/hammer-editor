package com.darkrockstudios.apps.hammer.email

import kotlinx.serialization.Serializable

@Serializable
data class MailgunConfig(
	val apiKey: String = "",
	val domain: String = "",
	val fromAddress: String = "",
	val fromName: String = "Hammer Server",
	val useEuRegion: Boolean = false,
)
