package com.darkrockstudios.apps.hammer.common.data.globalsettings

import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.TomlLiteralString

@Serializable
data class GlobalSettings(
	@TomlLiteralString
	val projectsDirectory: String,
	val uiTheme: UiTheme = UiTheme.FollowSystem,
	val automaticBackups: Boolean = true,
	val autoCloseSyncDialog: Boolean = true,
	val maxBackups: Int = DEFAULT_MAX_BACKUPS,
	val automaticSyncing: Boolean = true,
	val nux: NewUserExperience = NewUserExperience()
) {
	companion object {
		const val DEFAULT_MAX_BACKUPS = 50
	}
}

@Serializable
data class NewUserExperience(
	val exampleProjectCreated: Boolean = false
)

enum class UiTheme {
	Light,
	Dark,
	FollowSystem
}