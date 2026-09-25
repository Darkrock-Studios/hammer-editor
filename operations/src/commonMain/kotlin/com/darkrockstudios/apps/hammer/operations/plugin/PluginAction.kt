package com.darkrockstudios.apps.hammer.operations.plugin

import kotlinx.serialization.json.JsonObject

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

/** What an action's output is shown as. */
enum class ActionOutput(val id: String) {
	/** A line to show briefly. */
	Message("message"),

	/** Markdown shown in a dialog the user can copy or save as a note. */
	Document("document"),

	/** A document with the plugin's own buttons, each calling the action again. */
	Interactive("interactive");

	companion object {
		fun of(id: String): ActionOutput? = entries.firstOrNull { it.id == id }
	}
}

/** One input the user fills in before an action runs. */
sealed interface ActionField {
	val key: String
	val label: String
	val hint: String?

	/** A plain value, rendered and checked as a setting of the same type is. */
	class Setting(val declaration: SettingDeclaration) : ActionField {
		override val key get() = declaration.key
		override val label get() = declaration.label
		override val hint get() = declaration.hint
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
	) : ActionField
}

/**
 * An action a plugin adds to the menus of the screens in [places]. The host asks for [fields] first,
 * when there are any, then runs it.
 */
class PluginAction(
	val pluginId: String,
	/** Unique within the plugin. */
	val name: String,
	val label: String,
	val places: Set<ActionPlace>,
	val fields: List<ActionField>,
	val output: ActionOutput,
	/** Runs off the main thread. */
	val run: suspend (ActionCall) -> ActionReply,
)

/**
 * One run of an action: on [project], from [place], about the item with [itemId] there (null on the
 * project's own screen), with the user's [input], one value per field. [button] is the id of the
 * plugin's button that was pressed, for an interactive action's later calls.
 */
class ActionCall(
	val project: String,
	val place: ActionPlace,
	val itemId: Int?,
	val input: JsonObject,
	val button: String? = null,
	/** Called as the plugin reports progress, on the thread running it. */
	val onProgress: (ActionProgress) -> Unit = {},
	/** Checked each time the plugin reports progress; true stops it there. */
	val cancelled: () -> Boolean = { false },
)

/** How far an action has got: [fraction] from 0 to 1 when the plugin knows it, and a line saying what it is doing. */
class ActionProgress(val fraction: Float?, val message: String?)

/**
 * What an action returned. A [message] is shown briefly; [markdown] is shown in a dialog, with
 * [buttons] under it. Neither means it finished with nothing to say.
 */
class ActionReply(
	val message: String? = null,
	val markdown: String? = null,
	val buttons: List<ActionButton> = emptyList(),
)

class ActionButton(val id: String, val label: String)

/** Thrown out of [PluginAction.run] when [ActionCall.cancelled] stopped it. */
class ActionCancelledException : RuntimeException("Cancelled")
