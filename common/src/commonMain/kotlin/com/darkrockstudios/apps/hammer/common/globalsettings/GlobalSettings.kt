package com.darkrockstudios.apps.hammer.common.globalsettings

import kotlinx.serialization.Serializable

@Serializable
data class GlobalSettings(
	val projectsDirectory: String,
	val uiTheme: UiTheme = UiTheme.FollowSystem,
	val nux: NewUserExperience = NewUserExperience()
)

@Serializable
data class NewUserExperience(
	val exampleProjectCreated: Boolean = false
)

enum class UiTheme {
	Light,
	Dark,
	FollowSystem
}