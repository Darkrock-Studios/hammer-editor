package com.darkrockstudios.apps.hammer.frontend.data

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
	val userId: String,
	val username: String,
	val isAdmin: Boolean,
	val locale: String = "en"
)