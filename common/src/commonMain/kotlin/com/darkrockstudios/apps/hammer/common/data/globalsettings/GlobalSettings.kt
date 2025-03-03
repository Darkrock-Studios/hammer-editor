package com.darkrockstudios.apps.hammer.common.data.globalsettings

import com.darkrockstudios.apps.hammer.common.spellcheck.Language
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
	val nux: NewUserExperience = NewUserExperience(),
	val editorFontSize: Float = DEFAULT_FONT_SIZE,
	val spellCheckSettings: SpellCheckerSettings = SpellCheckerSettings(language = Language.English),
) {
	companion object {
		const val DEFAULT_MAX_BACKUPS = 50
		const val DEFAULT_FONT_SIZE = 16f
	}
}

@Serializable
data class NewUserExperience(
	val exampleProjectCreated: Boolean = false
)

@Serializable
data class SpellCheckerSettings(
	val enabled: Boolean = true,
	val enabledInFocusMode: Boolean = false,
	val language: Language
) {
	fun isEnabledInFocusMode(): Boolean = enabled && enabledInFocusMode
}

enum class UiTheme {
	Light,
	Dark,
	FollowSystem
}