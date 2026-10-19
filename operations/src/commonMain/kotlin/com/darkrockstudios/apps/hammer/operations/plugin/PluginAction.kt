package com.darkrockstudios.apps.hammer.operations.plugin

import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogFilter
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** The screens an action can be run from, each passing the item it shows. */
enum class ActionPlace(val id: String) {
	Project("project"),
	Scene("scene"),
	Note("note"),
	Entry("entry"),
	Event("event");

	companion object {
		fun of(id: String): ActionPlace? = entries.firstOrNull { it.id == id }
	}
}

/** One input on an action's page. Its value in [ActionCall.input] is keyed by [key]. */
sealed interface ActionField {
	val key: String
	val label: String
	val hint: String?

	/** The value the field starts with on its page. */
	val default: JsonElement

	/** A `bool`, `int`, or `choice`, rendered and checked as a setting of the same type is. */
	class Setting(val declaration: SettingDeclaration) : ActionField {
		override val key get() = declaration.key
		override val label get() = declaration.label
		override val hint get() = declaration.hint
		override val default: JsonElement get() = declaration.default
	}

	/** Text in an editor [format] picks; the value is markdown for the rich formats. Unless [required], blank is allowed. */
	class Text(
		override val key: String,
		override val label: String,
		override val hint: String? = null,
		val format: TextFormat = TextFormat.Plain,
		/** Only for [TextFormat.Plain]. */
		val multiline: Boolean = false,
		val required: Boolean = false,
		val defaultValue: String = "",
	) : ActionField {
		override val default: JsonElement get() = JsonPrimitive(defaultValue)
	}

	/**
	 * Scenes picked from the project's tree: a list of scene ids when [multiple], otherwise one id or
	 * null. An empty pick is allowed unless [required].
	 */
	class Scenes(
		override val key: String,
		override val label: String,
		override val hint: String? = null,
		val multiple: Boolean = true,
		val required: Boolean = false,
		/** The ids picked to begin with. */
		val defaultIds: List<Int> = emptyList(),
	) : ActionField {
		override val default: JsonElement
			get() = if (multiple) JsonArray(defaultIds.map(::JsonPrimitive)) else defaultIds.firstOrNull()?.let(::JsonPrimitive) ?: JsonNull
	}

	/**
	 * Items of the project picked by name: a list of `{"kind", "id"}` when [multiple], otherwise one or
	 * null. Only items [filter] accepts may be picked. An empty pick is allowed unless [required].
	 */
	class Entity(
		override val key: String,
		override val label: String,
		override val hint: String? = null,
		val filter: CatalogFilter = CatalogFilter(),
		val multiple: Boolean = false,
		val required: Boolean = false,
		/** The items picked to begin with. */
		val defaultRefs: List<CatalogRef> = emptyList(),
	) : ActionField {
		override val default: JsonElement get() = valueOf(defaultRefs)

		/** The value holding [refs]: all of them when [multiple], otherwise the first. */
		fun valueOf(refs: List<CatalogRef>): JsonElement =
			if (multiple) JsonArray(refs.map { it.toJson() }) else refs.firstOrNull()?.toJson() ?: JsonNull
	}
}

/** An entity field's value for one item, as plugins are sent it. */
fun CatalogRef.toJson(): JsonObject = JsonObject(mapOf(ENTITY_KIND to JsonPrimitive(kind.id), ENTITY_ID to JsonPrimitive(id)))

/** The item this names if it is `{"kind", "id"}` with a known kind, otherwise null. */
fun JsonElement.toCatalogRef(): CatalogRef? {
	val ref = this as? JsonObject ?: return null
	val kind = (ref[ENTITY_KIND] as? JsonPrimitive)?.takeIf { it.isString }?.let { CatalogKind.of(it.content) } ?: return null
	val id = (ref[ENTITY_ID] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return null
	return CatalogRef(kind, id)
}

/** The items an entity field's [value] names: a list, one, or none. What is not an item is passed over. */
fun entityRefs(value: JsonElement?): List<CatalogRef> = when (value) {
	is JsonArray -> value.mapNotNull { it.toCatalogRef() }.distinct()
	is JsonObject -> listOfNotNull(value.toCatalogRef())
	else -> emptyList()
}

private const val ENTITY_KIND = "kind"
private const val ENTITY_ID = "id"

/** The editor a [ActionField.Text] is shown in. */
enum class TextFormat(val id: String) {
	Plain("plain"),

	/** The scene editor's, with its format bar. */
	Rich("rich"),

	/** The rich editor with the extended format bar: highlight, task lists, and tables. */
	RichExtended("rich-extended");

	companion object {
		fun of(id: String): TextFormat? = entries.firstOrNull { it.id == id }
	}
}

/**
 * An action a plugin adds to the menus of the screens in [places]. Its [fields] are its first page,
 * which the host shows before calling it; without any, it is called at once.
 */
class PluginAction(
	val pluginId: String,
	/** Unique within the plugin. */
	val name: String,
	val label: String,
	val places: Set<ActionPlace>,
	val fields: List<ActionField>,
	/** The manifest's `keep`: whether a run should outlive its dialog being closed part way. */
	val keep: Boolean = false,
	/** The version of the plugin, which a kept run is resumed only by. */
	val pluginVersion: String? = null,
	/** Runs off the main thread. */
	val run: suspend (ActionCall) -> ActionReply,
)

/**
 * One call of an action: on [project], from [place], about the item with [itemId] there (null on
 * the project's own screen). [input] holds the values of the page the user was on, [button] the id
 * of the button they pressed there (null on the first call), and [state] the state of that page.
 */
class ActionCall(
	val project: String,
	val place: ActionPlace,
	val itemId: Int?,
	val input: JsonObject,
	val button: String? = null,
	/** Handed back untouched from the last page; null on the first call. */
	val state: JsonElement? = null,
	/** Reserved for fields that call the action when they change; always null. */
	val changed: String? = null,
	/** True on the first call after a kept run was restored. */
	val resumed: Boolean = false,
	/** Called as the plugin reports progress, on the thread running it. */
	val onProgress: (ActionProgress) -> Unit = {},
	/** Checked each time the plugin reports progress; true stops it there. Cancelling the caller stops it at once. */
	val cancelled: () -> Boolean = { false },
)

/** How far an action has got: [fraction] from 0 to 1 when the plugin knows it, and a line saying what it is doing. */
class ActionProgress(val fraction: Float?, val message: String?)

/**
 * What an action returned. [errors] keeps the user on the page they were on; otherwise [open] ends
 * the run and shows an item, [page] is shown next, and with neither the run ends. A [message] is
 * shown briefly either way.
 */
class ActionReply(
	val page: ActionPage? = null,
	val message: String? = null,
	/** By field key; a key that is not a field on the page, such as "", is about the whole page. */
	val errors: Map<String, String> = emptyMap(),
	val open: ActionTarget? = null,
)

/** What an action's dialog shows: every part optional. */
class ActionPage(
	val title: String? = null,
	val markdown: String? = null,
	val fields: List<ActionField> = emptyList(),
	val buttons: List<ActionButton> = emptyList(),
	/** The plugin's own, handed back untouched with the next call from this page. */
	val state: JsonElement? = null,
)

/**
 * A button on an [ActionPage]: in the dialog's footer, or under the markdown when [inline], for one
 * that acts on an item in it. Unless [validate] is false, pressing it checks the page's fields first.
 */
class ActionButton(
	val id: String,
	val label: String,
	val primary: Boolean = false,
	val inline: Boolean = false,
	val validate: Boolean = true,
)

/** An item to show: the project's home, or the scene, note, entry, or event with [id]. */
class ActionTarget(val place: ActionPlace, val id: Int?)

/** Thrown out of [PluginAction.run] when [ActionCall.cancelled] stopped it. */
class ActionCancelledException : RuntimeException("Cancelled")
