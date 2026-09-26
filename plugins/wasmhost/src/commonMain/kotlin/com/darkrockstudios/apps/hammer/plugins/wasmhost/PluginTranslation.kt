package com.darkrockstudios.apps.hammer.plugins.wasmhost

import com.darkrockstudios.apps.hammer.operations.plugin.FieldOption
import com.darkrockstudios.apps.hammer.operations.plugin.FieldTable
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml

/**
 * A package's `locales/<tag>.toml`: its manifest's and settings' words in one language, keyed as the
 * manifest keys them. Anything left out stays as the manifest has it.
 *
 *     name = "Générateur de noms"
 *
 *     [actions.names]
 *     label = "Générer des noms"
 *
 *     [actions.names.fields.style]
 *     label = "Style"
 *     options = { everyday = "Anglais courant", norse = "Nordique" }
 */
@Serializable
class PluginTranslation(
	val name: String? = null,
	val description: String? = null,
	/** By export format. */
	val exporters: Map<String, Text> = emptyMap(),
	val actions: Map<String, ActionText> = emptyMap(),
	val diagnostics: Map<String, Text> = emptyMap(),
	val commands: Map<String, Text> = emptyMap(),
	val settings: Map<String, Text> = emptyMap(),
) {
	/** A label, hint, command help, or choice's option labels by value; whichever the thing has. */
	@Serializable
	class Text(
		val label: String? = null,
		val hint: String? = null,
		val help: String? = null,
		val options: Map<String, String> = emptyMap(),
	)

	@Serializable
	class ActionText(val label: String? = null, val fields: Map<String, Text> = emptyMap())

	companion object {
		private val toml = Toml { ignoreUnknownKeys = true }

		/** Throws [IllegalArgumentException] when [text] is not a translation. */
		fun parse(text: String): PluginTranslation = toml.decodeFromString(serializer(), text)

		/**
		 * The translations for [locale], most specific first: its exact tag, then its language alone.
		 * Tags match ignoring case, and `_` as `-`.
		 */
		fun forLocale(translations: Map<String, PluginTranslation>, locale: String?): List<PluginTranslation> {
			if (locale.isNullOrBlank()) return emptyList()
			val wanted = normalized(locale)
			val byTag = translations.mapKeys { (tag, _) -> normalized(tag) }
			return listOfNotNull(byTag[wanted], byTag[wanted.substringBefore('-')].takeIf { '-' in wanted })
		}

		fun normalized(tag: String) = tag.replace('_', '-').lowercase()
	}
}

/** [manifest]'s words in [translations], most specific first, falling back to its own. */
internal fun translate(manifest: PluginManifest, translations: List<PluginTranslation>): PluginManifest {
	if (translations.isEmpty()) return manifest
	fun <T> first(pick: (PluginTranslation) -> T?): T? = translations.firstNotNullOfOrNull(pick)
	return manifest.copy(
		name = first { it.name } ?: manifest.name,
		description = first { it.description } ?: manifest.description,
		exporters = manifest.exporters.map { exporter ->
			exporter.copy(label = first { it.exporters[exporter.format]?.label } ?: exporter.label)
		},
		actions = manifest.actions.map { action ->
			action.copy(
				label = first { it.actions[action.name]?.label } ?: action.label,
				field = action.field.map { field -> field.translated(translations.mapNotNull { it.actions[action.name]?.fields?.get(field.key) }) },
			)
		},
		diagnostics = manifest.diagnostics.map { check ->
			check.copy(label = first { it.diagnostics[check.name]?.label } ?: check.label)
		},
		commands = manifest.commands.map { command ->
			command.copy(help = first { it.commands[command.name]?.help } ?: command.help)
		},
	)
}

/** [settings] in [translations], most specific first, falling back to their own words. */
internal fun translate(settings: List<SettingDeclaration>, translations: List<PluginTranslation>): List<SettingDeclaration> {
	if (translations.isEmpty()) return settings
	return settings.map { setting ->
		val texts = translations.mapNotNull { it.settings[setting.key] }
		if (texts.isEmpty()) return@map setting
		val label = texts.firstNotNullOfOrNull { it.label } ?: setting.label
		val hint = texts.firstNotNullOfOrNull { it.hint } ?: setting.hint
		when (setting) {
			is SettingDeclaration.Toggle -> SettingDeclaration.Toggle(setting.key, label, setting.defaultValue, hint)
			is SettingDeclaration.Number -> SettingDeclaration.Number(setting.key, label, setting.defaultValue, setting.min, setting.max, hint)
			is SettingDeclaration.Text -> SettingDeclaration.Text(setting.key, label, setting.defaultValue, setting.multiline, hint)
			is SettingDeclaration.Choice -> SettingDeclaration.Choice(
				setting.key,
				label,
				setting.defaultValue,
				setting.options.map { option ->
					SettingDeclaration.Choice.Option(option.value, texts.firstNotNullOfOrNull { it.options[option.value] } ?: option.label)
				},
				hint,
			)
		}
	}
}

private fun FieldTable.translated(texts: List<PluginTranslation.Text>): FieldTable {
	if (texts.isEmpty()) return this
	return FieldTable(
		key = key,
		type = type,
		label = texts.firstNotNullOfOrNull { it.label } ?: label,
		default = default,
		hint = texts.firstNotNullOfOrNull { it.hint } ?: hint,
		min = min,
		max = max,
		multiline = multiline,
		options = options.map { option -> FieldOption(option.value, texts.firstNotNullOfOrNull { it.options[option.value] } ?: option.label) },
		multiple = multiple,
		required = required,
	)
}
