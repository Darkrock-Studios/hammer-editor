package com.darkrockstudios.apps.hammer.common.data.globalsettings

import io.fluidsonic.locale.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.peanuuutz.tomlkt.TomlLiteralString

@Serializable
data class GlobalSettings(
	@TomlLiteralString
	val projectsDirectory: String,
	val uiTheme: UiTheme = UiTheme.FollowSystem,
	val automaticBackups: Boolean = true,
	val autoCloseSyncDialog: Boolean = true,
	val maxBackups: Int = DEFAULT_BACKUPS,
	val automaticSyncing: Boolean = true,
	val nux: NewUserExperience = NewUserExperience(),
	val editorFontSize: Float = DEFAULT_FONT_SIZE,
	val spellCheckSettings: SpellCheckerSettings = SpellCheckerSettings(
		locale = Locale.forLanguage(
			language = "en",
			region = "US"
		)
	),
) {
	companion object {

		const val DEFAULT_BACKUPS = 20
		const val MAX_BACKUPS = 50
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
	@Serializable(with = LocaleSerializer::class)
	val locale: Locale
) {
	fun isEnabledInFocusMode(): Boolean = enabled && enabledInFocusMode
}

enum class UiTheme {
	Light,
	Dark,
	FollowSystem
}

object LocaleSerializer : KSerializer<Locale> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("Locale", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: Locale) {
		encoder.encodeString(value.toLanguageTag().toString())
	}

	override fun deserialize(decoder: Decoder): Locale {
		return Locale.forLanguageTag(decoder.decodeString())
	}
}

object LocaleListSerializer : KSerializer<List<Locale>> by ListSerializer(LocaleSerializer)