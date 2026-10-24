package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogFilter
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import net.peanuuutz.tomlkt.Toml
import net.peanuuutz.tomlkt.TomlArray
import net.peanuuutz.tomlkt.TomlDecoder
import net.peanuuutz.tomlkt.TomlElement
import net.peanuuutz.tomlkt.TomlEncoder
import net.peanuuutz.tomlkt.TomlLiteral
import net.peanuuutz.tomlkt.TomlNull
import net.peanuuutz.tomlkt.TomlTable
import net.peanuuutz.tomlkt.toBooleanOrNull
import net.peanuuutz.tomlkt.toDoubleOrNull
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
		/** Turning it on lets the plugin do something risky, which the form warns of. */
		val danger: Boolean = false,
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

	/**
	 * A password or API key: kept in the platform's encrypted store rather than the settings file, and
	 * masked in the form. The plugin gets it as a string, empty when unset.
	 */
	class Secret(
		override val key: String,
		override val label: String,
		override val hint: String? = null,
	) : SettingDeclaration() {
		override val default = JsonPrimitive("")
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
 * `int`, `danger` for `bool`, `multiline` for `string`, and `options` for `choice`. A `secret` has no default.
 */
fun parseSettingDeclarations(toml: String): List<SettingDeclaration> =
	Toml { ignoreUnknownKeys = true }.decodeFromString(SettingsFile.serializer(), toml).setting.map { it.toDeclaration() }

@Serializable
private class SettingsFile(val setting: List<FieldTable> = emptyList())

/**
 * A field as a plugin declares it: a `[[setting]]` in `settings.toml`, an action's
 * `[[actions.field]]`, or a field of a page an action replies with in JSON. Action fields may also be
 * `text`, with `format`; `scenes`, with `multiple`; and `entity`, with `kinds`, `entry_types`, `tags`,
 * and `multiple`. `required` applies to those three.
 */
@Serializable
class FieldTable(
	val key: String,
	val type: String,
	val label: String,
	@Serializable(with = FieldDefaultSerializer::class)
	val default: JsonElement? = null,
	val hint: String? = null,
	val min: Long? = null,
	val max: Long? = null,
	val multiline: Boolean = false,
	/** For `bool`: see [SettingDeclaration.Toggle.danger]. */
	val danger: Boolean = false,
	val options: List<FieldOption> = emptyList(),
	/** True by default for `scenes`, false for `entity`. */
	val multiple: Boolean? = null,
	val required: Boolean = false,
	/** A [TextFormat] id, for `text`. */
	val format: String? = null,
	/** [CatalogKind] ids, for `entity`; every kind when left out. */
	val kinds: List<String>? = null,
	/** [EntryType] ids, for an `entity` that takes entries; every type when empty. */
	@SerialName("entry_types")
	val entryTypes: List<String> = emptyList(),
	/** For `entity`: only items carrying one of these may be picked; any when empty. */
	val tags: List<String> = emptyList(),
) {
	/** Throws [IllegalArgumentException] for a type an action cannot have. */
	fun toActionField(): ActionField = when (type) {
		SCENES -> {
			val multiple = multiple ?: true
			ActionField.Scenes(
				key = key,
				label = label,
				hint = hint,
				multiple = multiple,
				required = required,
				defaultIds = when (val ids = default) {
					null, JsonNull -> emptyList()
					is JsonArray if multiple -> ids.map { it.sceneId() }
					is JsonPrimitive if !multiple -> listOf(ids.sceneId())
					else -> throw IllegalArgumentException("Field '$key' has an invalid default")
				},
			)
		}
		ENTITY -> toEntityField()
		TEXT -> ActionField.Text(
			key = key,
			label = label,
			hint = hint,
			format = format?.let { TextFormat.of(it) ?: throw IllegalArgumentException("Field '$key' has unknown format '$it'") }
				?: TextFormat.Plain,
			multiline = multiline,
			required = required,
			defaultValue = default?.let { text ->
				(text as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("Field '$key' has an invalid default")
			}.orEmpty(),
		)
		STRING -> throw IllegalArgumentException("Field '$key' is a string: an action asks for text with type 'text'")
		SECRET -> throw IllegalArgumentException("Field '$key' is a secret, which only a setting may be")
		// toDeclaration() quietly replaces a default of the wrong type, as a setting's is; an action's is refused.
		else -> ActionField.Setting(toDeclaration()).also { field ->
			require(default == null || (default as? JsonPrimitive)?.let(field.declaration::accept) != null) {
				"Field '$key' has an invalid default"
			}
		}
	}

	fun toDeclaration(): SettingDeclaration {
		val default = default as? JsonPrimitive
		return when (type) {
			BOOL -> SettingDeclaration.Toggle(key, label, default?.booleanOrNull ?: false, hint, danger)
			INT -> SettingDeclaration.Number(key, label, default?.longOrNull ?: min ?: 0, min, max, hint)
			STRING -> SettingDeclaration.Text(key, label, default?.content ?: "", multiline, hint)
			SECRET -> {
				require(default == null) { "Field '$key' is a secret, which cannot have a default" }
				SettingDeclaration.Secret(key, label, hint)
			}
			CHOICE -> SettingDeclaration.Choice(
				key = key,
				label = label,
				defaultValue = default?.content ?: options.firstOrNull()?.value.orEmpty(),
				options = options.map { SettingDeclaration.Choice.Option(it.value, it.label) },
				hint = hint,
			)
			else -> throw IllegalArgumentException("Field '$key' has unknown type '$type'")
		}
	}

	private fun toEntityField(): ActionField.Entity {
		val kinds = kinds?.mapTo(LinkedHashSet()) { id ->
			CatalogKind.of(id) ?: throw IllegalArgumentException("Field '$key' has unknown kind '$id'")
		} ?: CatalogKind.entries.toSet()
		require(kinds.isNotEmpty()) { "Field '$key' has no kinds to pick from" }
		val entryTypes = entryTypes.mapTo(LinkedHashSet()) { id ->
			EntryType.entries.firstOrNull { it.text == id } ?: throw IllegalArgumentException("Field '$key' has unknown entry type '$id'")
		}
		require(entryTypes.isEmpty() || CatalogKind.Entry in kinds) { "Field '$key' has entry_types, but does not take entries" }
		require(tags.none { it.isBlank() }) { "Field '$key' has a blank tag" }
		val multiple = multiple ?: false
		return ActionField.Entity(
			key = key,
			label = label,
			hint = hint,
			filter = CatalogFilter(kinds, entryTypes, tags.toSet()),
			multiple = multiple,
			required = required,
			defaultRefs = when (val refs = default) {
				null, JsonNull -> emptyList()
				is JsonArray if multiple -> refs.map { it.entityRef() }
				is JsonObject if !multiple -> listOf(refs.entityRef())
				else -> throw IllegalArgumentException("Field '$key' has an invalid default")
			},
		)
	}

	private fun JsonElement.entityRef(): CatalogRef = toCatalogRef() ?: throw IllegalArgumentException("Field '$key' has an invalid default")

	private fun JsonElement.sceneId(): Int =
		(this as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: throw IllegalArgumentException("Field '$key' has an invalid default")
}

@Serializable
class FieldOption(val value: String, val label: String)

/**
 * The fields of one page as an action's, in order. Throws [IllegalArgumentException] when one cannot
 * be shown: an unknown type, no key, a key used twice, a choice with no options, or a default the
 * field would not accept.
 */
fun List<FieldTable>.toActionFields(): List<ActionField> {
	val fields = map { it.toActionField() }
	fields.groupBy { it.key }.filterValues { it.size > 1 }.keys.takeIf { it.isNotEmpty() }?.let {
		throw IllegalArgumentException("Fields declared twice: $it")
	}
	fields.forEach { field ->
		require(field.key.isNotBlank()) { "A field has no key" }
		val declaration = (field as? ActionField.Setting)?.declaration ?: return@forEach
		require(declaration !is SettingDeclaration.Choice || declaration.options.isNotEmpty()) {
			"Field '${field.key}' is a choice with no options"
		}
		require(declaration.accept(declaration.default) != null) { "Field '${field.key}' has an invalid default" }
	}
	return fields
}

/** This field as a plugin would declare it, which [FieldTable.toActionField] reads back the same. */
fun ActionField.toFieldTable(): FieldTable = when (this) {
	is ActionField.Text -> FieldTable(key, TEXT, label, default, hint, multiline = multiline, required = required, format = format.id)
	is ActionField.Scenes -> FieldTable(key, SCENES, label, default, hint, multiple = multiple, required = required)
	is ActionField.Entity -> FieldTable(
		key = key,
		type = ENTITY,
		label = label,
		default = default,
		hint = hint,
		multiple = multiple,
		required = required,
		kinds = filter.kinds.map { it.id },
		entryTypes = filter.entryTypes.map { it.text },
		tags = filter.tags.toList(),
	)
	is ActionField.Setting -> when (val declaration = declaration) {
		is SettingDeclaration.Toggle -> FieldTable(key, BOOL, label, declaration.default, hint, danger = declaration.danger)
		is SettingDeclaration.Number -> FieldTable(key, INT, label, declaration.default, hint, min = declaration.min, max = declaration.max)
		is SettingDeclaration.Choice -> FieldTable(
			key = key,
			type = CHOICE,
			label = label,
			default = declaration.default,
			hint = hint,
			options = declaration.options.map { FieldOption(it.value, it.label) },
		)
		// Never read from a plugin, which asks for text with type `text`.
		is SettingDeclaration.Text -> FieldTable(key, TEXT, label, declaration.default, hint, multiline = declaration.multiline)
		is SettingDeclaration.Secret -> FieldTable(key, SECRET, label, null, hint)
	}
}

/** A field's `default` as JSON, read the same from a TOML manifest as from a JSON page. */
internal object FieldDefaultSerializer : KSerializer<JsonElement> {
	override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

	override fun deserialize(decoder: Decoder): JsonElement = when (decoder) {
		is JsonDecoder -> decoder.decodeJsonElement()
		is TomlDecoder -> decoder.decodeTomlElement().toJsonElement()
		else -> throw SerializationException("A field's default is read only from JSON or TOML")
	}

	override fun serialize(encoder: Encoder, value: JsonElement) = when (encoder) {
		is JsonEncoder -> encoder.encodeJsonElement(value)
		is TomlEncoder -> encoder.encodeTomlElement(value.toTomlElement())
		else -> throw SerializationException("A field's default is written only as JSON or TOML")
	}
}

private fun TomlElement.toJsonElement(): JsonElement = when (this) {
	TomlNull -> JsonNull
	is TomlLiteral -> when (type) {
		TomlLiteral.Type.Boolean -> toBooleanOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(content)
		TomlLiteral.Type.Integer -> toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(content)
		TomlLiteral.Type.Float -> toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(content)
		// Strings, and dates and times as their text.
		else -> JsonPrimitive(content)
	}
	is TomlArray -> JsonArray(map { it.toJsonElement() })
	is TomlTable -> JsonObject(mapValues { (_, value) -> value.toJsonElement() })
}

private fun JsonElement.toTomlElement(): TomlElement = when (this) {
	JsonNull -> TomlNull
	is JsonPrimitive -> when {
		isString -> TomlLiteral(content)
		booleanOrNull != null -> TomlLiteral(booleanOrNull!!)
		longOrNull != null -> TomlLiteral(longOrNull!!)
		else -> TomlLiteral(doubleOrNull ?: 0.0)
	}
	is JsonArray -> TomlArray(map { it.toTomlElement() })
	is JsonObject -> TomlTable(mapValues { (_, value) -> value.toTomlElement() })
}

private const val BOOL = "bool"
private const val INT = "int"
private const val CHOICE = "choice"
private const val SCENES = "scenes"
private const val ENTITY = "entity"
private const val TEXT = "text"
private const val STRING = "string"
private const val SECRET = "secret"
