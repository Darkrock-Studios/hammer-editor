package com.darkrockstudios.apps.hammer.common.data.globalsettings

import androidx.compose.runtime.Immutable
import com.darkrockstudios.apps.hammer.common.util.Locale
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.peanuuutz.tomlkt.TomlLiteralString

@Immutable
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
	val enableDndInFocusMode: Boolean = false,
	/**
	 * Whether the scene metadata panel is visible on wide layouts. UI state,
	 * not really a user "preference" — lives here because we don't have a
	 * dedicated window/UI-state store. Ignored on narrow layouts where the
	 * panel renders as a Dialog and uses transient state instead.
	 */
	val metadataPanelVisible: Boolean = true,
	/**
	 * Whether the desktop project navigation rail is expanded (full labels)
	 * vs. collapsed (icons + short labels). UI state persisted here for the
	 * same reason as [metadataPanelVisible].
	 */
	val navRailExpanded: Boolean = false,
	val spellCheckSettings: SpellCheckerSettings = SpellCheckerSettings(
		locale = Locale.forLanguage(
			language = "en",
			region = "US"
		)
	),
	val installId: String? = null,
	val deviceLabel: String? = null,
	val initialProjectScreen: InitialProjectScreen = InitialProjectScreen.Home,
	val lastDismissedUpdateVersion: String? = null,
) {
	companion object {

		const val DEFAULT_BACKUPS = 20
		const val MAX_BACKUPS = 50
		const val DEFAULT_FONT_SIZE = 16f
	}
}

@Immutable
@Serializable
data class NewUserExperience(
	val exampleProjectCreated: Boolean = false
)

@Immutable
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

enum class InitialProjectScreen {
	Home,
	Editor
}

object LocaleSerializer : KSerializer<Locale> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("Locale", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: Locale) {
		encoder.encodeString(value.toLanguageTag())
	}

	override fun deserialize(decoder: Decoder): Locale {
		return Locale.forLanguageTag(decoder.decodeString())
	}
}

object LocaleListSerializer : KSerializer<List<Locale>> by ListSerializer(LocaleSerializer)