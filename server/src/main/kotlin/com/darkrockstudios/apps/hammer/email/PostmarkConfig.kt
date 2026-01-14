package com.darkrockstudios.apps.hammer.email

import kotlinx.serialization.Serializable

@Serializable
data class PostmarkConfig(
	val serverToken: String = "",
	val fromAddress: String = "",
	val fromName: String = "Hammer Server",
)
