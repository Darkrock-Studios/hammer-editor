package com.darkrockstudios.apps.hammer.common.globalsettings

import kotlinx.serialization.Serializable

@Serializable
data class GlobalSettings(
	val projectsDirectory: String
)