package com.darkrockstudios.apps.hammer.admin

import kotlinx.serialization.Serializable

@Serializable
data class WhiteListConfig(
	val enabled: Boolean = true,
)