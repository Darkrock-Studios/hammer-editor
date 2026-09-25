package com.darkrockstudios.apps.hammer.operations.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.toBooleanOrNull
import net.peanuuutz.tomlkt.toLongOrNull

/**
 * One typed plugin setting, which the host renders as a form field. Values are JSON primitives in
 * memory and literals in the plugin's settings file, one key per setting.
 */
sealed class SettingDeclaration {
	/** Also the key in the settings file and in the settings a plugin is sent. */
	abstract val key: String

	abstract val label: String
	abstract val hint: String?
	abstract val default: JsonPrimitive

	/** [value] if it is valid for this setting, otherwise null. */
	abstract fun accept(value: JsonPrimitive): JsonPrimitive?

	class Toggle(
		override val key: String,
		override val label: String,
		val defaultValue: Boolean,
		override val hint: String? = null,
	) : SettingDeclaration() {
		override val default = JsonPrimitive(defaultValue)
		override fun accept(value: JsonPrimitive) = value.takeIf { !it.isString && it.booleanOrNull != null }
	}

	class Number(
		override val key: String,
		override val label: String,
		val defaultValue: Long,
		val min: Long? = null,
		val max: Long? = null,
		override val hint: String? = null,
	) : SettingDeclaration() {
		override val default = JsonPrimitive(defaultValue)
		override fun accept(value: JsonPrimitive): JsonPrimitive? {
			val number = value.takeIf { !it.isString }?.longOrNull ?: return null
			return value.takeIf { (min == null || number >= min) && (max == null || number <= max) }
		}
	}

	class Text(
		override val key: String,
		override val label: String,
		val defaultValue: String = "",
		val multiline: Boolean = false,
		override val hint: String? = null,
	) : SettingDeclaration() {
		override val default = JsonPrimitive(defaultValue)
		override fun accept(value: JsonPrimitive) = value.takeIf { it.isString }
	}

	class Choice(
		override val key: String,
		override val label: String,
		val defaultValue: String,
		val options: List<Option>,
		override val hint: String? = null,
	) : SettingDeclaration() {
		class Option(val value: String, val label: String)

		override val default = JsonPrimitive(defaultValue)
		override fun accept(value: JsonPrimitive) = value.takeIf { it.isString && options.any { o -> o.value == it.content } }
	}
}

/** Checks a plugin's declarations are usable: unique keys, valid defaults, and choices with options. */
fun validateSettings(pluginId: String, settings: List<SettingDeclaration>) {
	val duplicates = settings.groupBy { it.key }.filterValues { it.size > 1 }.keys
	require(duplicates.isEmpty()) { "Plugin '$pluginId' declares settings more than once: $duplicates" }
	settings.forEach { setting ->
		require(setting.key.isNotBlank()) { "Plugin '$pluginId' declares a setting with no key" }
		require(setting !is SettingDeclaration.Choice || setting.options.isNotEmpty()) {
			"Plugin '$pluginId' setting '${setting.key}' is a choice with no options"
		}
		require(setting.accept(setting.default) != null) {
			"Plugin '$pluginId' setting '${setting.key}' has an invalid default"
		}
	}
}

/** Every declared setting's value from [stored], or its default where the stored value is missing or invalid. */
fun List<SettingDeclaration>.resolve(stored: Map<String, JsonPrimitive>): JsonObject =
	JsonObject(associate { setting -> setting.key to (stored[setting.key]?.let(setting::accept) ?: setting.default) })

internal fun TomlTable.toSettingValues(): Map<String, JsonPrimitive> = mapNotNull { (key, element) ->
	element.toJsonPrimitive()?.let { key to it }
}.toMap()

internal fun JsonObject.toTomlTable(): TomlTable = TomlTable(
	mapValues { (_, value) ->
		val primitive = value as JsonPrimitive
		when {
			primitive.isString -> TomlLiteral(primitive.content)
			primitive.booleanOrNull != null -> TomlLiteral(primitive.booleanOrNull!!)
			else -> TomlLiteral(primitive.longOrNull ?: 0L)
		}
	}
)

private fun TomlElement.toJsonPrimitive(): JsonPrimitive? {
	val literal = this as? TomlLiteral ?: return null
	return when (literal.type) {
		TomlLiteral.Type.Boolean -> literal.toBooleanOrNull()?.let(::JsonPrimitive)
		TomlLiteral.Type.Integer -> literal.toLongOrNull()?.let(::JsonPrimitive)
		TomlLiteral.Type.String -> JsonPrimitive(literal.content)
		else -> null
	}
}

/**
 * Parses a runtime plugin's `settings.toml`: an array of `[[setting]]` tables, each with `key`, `type`
 * (`bool`, `int`, `string`, or `choice`), `label`, and `default`, plus `hint`, `min` and `max` for
 * `int`, `multiline` for `string`, and `options` for `choice`.
 */
fun parseSettingDeclarations(toml: String): List<SettingDeclaration> =
	Toml { ignoreUnknownKeys = true }.decodeFromString(SettingsFile.serializer(), toml).setting.map { it.toDeclaration() }

@Serializable
private class SettingsFile(val setting: List<FieldTable> = emptyList())

/**
 * A field as a plugin declares it in TOML: a `[[setting]]` in `settings.toml`, or an action's
 * `[[actions.field]]`, which may also be `scenes`, with `multiple` and `required`.
 */
@Serializable
class FieldTable(
	val key: String,
	val type: String,
	val label: String,
	val default: TomlElement? = null,
	val hint: String? = null,
	val min: Long? = null,
	val max: Long? = null,
	val multiline: Boolean = false,
	val options: List<FieldOption> = emptyList(),
	val multiple: Boolean = true,
	val required: Boolean = false,
) {
	/** Throws [IllegalArgumentException] for an unknown type. */
	fun toActionField(): ActionField =
		if (type == SCENES) ActionField.Scenes(key, label, hint, multiple, required) else ActionField.Setting(toDeclaration())

	fun toDeclaration(): SettingDeclaration {
		val default = default?.toJsonPrimitive()
		return when (type) {
			"bool" -> SettingDeclaration.Toggle(key, label, default?.booleanOrNull ?: false, hint)
			"int" -> SettingDeclaration.Number(key, label, default?.longOrNull ?: min ?: 0, min, max, hint)
			"string" -> SettingDeclaration.Text(key, label, default?.content ?: "", multiline, hint)
			"choice" -> SettingDeclaration.Choice(
				key = key,
				label = label,
				defaultValue = default?.content ?: options.firstOrNull()?.value.orEmpty(),
				options = options.map { SettingDeclaration.Choice.Option(it.value, it.label) },
				hint = hint,
			)
			else -> throw IllegalArgumentException("Setting '$key' has unknown type '$type'")
		}
	}
}

@Serializable
class FieldOption(val value: String, val label: String)

private const val SCENES = "scenes"
