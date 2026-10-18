package com.darkrockstudios.apps.hammer.common.compose.plugin

import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.composeui.resources.Res
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_busy
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_failed
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_open_missing
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_save_failed
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_saved
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_saved_cut
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_stopped
import com.darkrockstudios.apps.hammer.operations.OperationJson
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.core.SceneKind
import com.darkrockstudios.apps.hammer.operations.core.SceneNode
import com.darkrockstudios.apps.hammer.operations.core.SceneTree
import com.darkrockstudios.apps.hammer.operations.plugin.ActionButton
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCall
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCancelledException
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPage
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionProgress
import com.darkrockstudios.apps.hammer.operations.plugin.ActionReply
import com.darkrockstudios.apps.hammer.operations.plugin.ActionTarget
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import io.github.aakira.napier.Napier
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.StringResource

/** One run of an action, from opening it to its end: the action, and where it was started. */
class ActionRun(val action: PluginAction, val place: ActionPlace, val itemId: Int?)

/** What is wrong with a field's value: the plugin's word on it, or a check the host makes before calling. */
sealed interface FieldError {
	data class Plugin(val message: String) : FieldError

	/** A required text left blank, or required scenes left unpicked. */
	data object Required : FieldError

	data class OutOfRange(val min: Long?, val max: Long?) : FieldError

	data object NotAnOption : FieldError
}

/** What the runner is showing. */
sealed interface ActionRunState {
	val run: ActionRun

	/**
	 * A [page], with the [values] of its fields so far and the [errors] in them by field key; the key
	 * "" is about the whole page. The [first] page is the action's own fields, run by the host's Run.
	 */
	data class Page(
		override val run: ActionRun,
		val page: ActionPage,
		val values: JsonObject,
		val errors: Map<String, FieldError> = emptyMap(),
		val first: Boolean = false,
		val saving: Boolean = false,
	) : ActionRunState

	/** A call in progress, made from the page [from] if there was one, which an `errors` reply goes back to. */
	data class Working(
		override val run: ActionRun,
		val from: Page?,
		val progress: ActionProgress? = null,
	) : ActionRunState
}

/**
 * Runs plugin actions for one open project, one at a time: shows their pages, checks what the user
 * entered before calling, shows progress and what each call replies, and remembers the values given
 * each action's first page for next time.
 */
class PluginActionRunner(
	private val project: ProjectDef,
	private val scope: CoroutineScope,
	private val operations: OperationRegistry,
	private val strings: StrRes,
	private val showMessage: (String) -> Unit,
	/** Shows the project's home, or the item at a place with an id; false when there is no such item. */
	private val navigate: suspend (ActionPlace, Int?) -> Boolean,
	private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
	private val _state = MutableStateFlow<ActionRunState?>(null)
	val state: StateFlow<ActionRunState?> = _state.asStateFlow()

	private val _scenes = MutableStateFlow<List<ExportableScene>?>(null)

	/** The project's scenes for a scenes field, loaded when a page asks for one. */
	val scenes: StateFlow<List<ExportableScene>?> = _scenes.asStateFlow()

	private val remembered = mutableMapOf<String, JsonObject>()
	private var job: Job? = null
	private var stop: Stop? = null

	fun start(action: PluginAction, place: ActionPlace, itemId: Int?) {
		when (_state.value) {
			null -> Unit
			is ActionRunState.Working -> return message(Res.string.plugin_action_busy)
			else -> return
		}
		val run = ActionRun(action, place, itemId)
		if (action.fields.isEmpty()) {
			call(run, from = null, button = null)
		} else {
			show(ActionRunState.Page(run, ActionPage(fields = action.fields), firstValues(action), first = true))
		}
	}

	/** Sets a field of the page showing, clearing any error on it. */
	fun change(key: String, value: JsonElement) {
		_state.update { state ->
			if (state is ActionRunState.Page) state.copy(values = JsonObject(state.values + (key to value)), errors = state.errors - key) else state
		}
	}

	/**
	 * Calls the action with the page's values once they pass the host's checks, which [button] can
	 * skip. A null [button] is the first page's Run.
	 */
	fun press(button: ActionButton?) {
		val page = _state.value as? ActionRunState.Page ?: return
		if (page.saving) return
		if (button?.validate != false) {
			val errors = hostErrors(page.page.fields, page.values)
			if (errors.isNotEmpty()) {
				_state.value = page.copy(errors = errors)
				return
			}
		}
		if (page.first) remembered[page.run.action.key] = page.values
		call(page.run, from = page, button = button?.id)
	}

	/** Stops the call in progress at once: cancelling it interrupts the plugin. */
	fun stop() {
		if (_state.value !is ActionRunState.Working) return
		stop?.requested = true
		job?.cancel()
		_state.value = null
		message(Res.string.plugin_action_stopped)
	}

	fun dismiss() {
		if (_state.value !is ActionRunState.Working) _state.value = null
	}

	/** Saves the markdown of a page without fields as a note, cut at a line break if it is too long for one. */
	fun saveAsNote() {
		val showing = _state.value as? ActionRunState.Page ?: return
		if (showing.page.fields.isNotEmpty()) return
		val markdown = showing.page.markdown?.trim() ?: return
		// A second press while the first save runs would make a second note.
		if (showing.saving) return
		_state.value = showing.copy(saving = true)
		scope.launch {
			val fits = markdown.length <= NotesRepository.MAX_NOTE_SIZE
			val text = if (fits) markdown else cutToFit(markdown, NotesRepository.MAX_NOTE_SIZE)
			val saved = try {
				operations.dispatch(NOTE_CREATE, buildJsonObject {
					put("project", project.name)
					put("content", text)
				})
				true
			} catch (e: CancellationException) {
				throw e
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				Napier.e(e) { "Could not save an action's page as a note" }
				false
			}
			_state.update { state ->
				if (state is ActionRunState.Page && state.page === showing.page) (if (saved) null else state.copy(saving = false)) else state
			}
			message(
				when {
					!saved -> Res.string.plugin_action_save_failed
					fits -> Res.string.plugin_action_saved
					else -> Res.string.plugin_action_saved_cut
				}
			)
		}
	}

	private fun call(run: ActionRun, from: ActionRunState.Page?, button: String?) {
		val stop = Stop().also { this.stop = it }
		_state.value = ActionRunState.Working(run, from)
		job = scope.launch {
			val reply = try {
				withContext(workDispatcher) {
					run.action.run(
						ActionCall(
							project = project.name,
							place = run.place,
							itemId = run.itemId,
							input = from?.values ?: JsonObject(emptyMap()),
							button = button,
							state = from?.page?.state,
							onProgress = { progress -> report(run, progress) },
							cancelled = { stop.requested },
						)
					)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: ActionCancelledException) {
				return@launch
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				// A plugin's failure must not take the screen down with it.
				Napier.e(e) { "Plugin action '${run.action.name}' failed" }
				_state.value = null
				message(Res.string.plugin_action_failed)
				return@launch
			}
			answer(run, from, reply)
		}
	}

	private fun report(run: ActionRun, progress: ActionProgress) {
		_state.update { state -> if (state is ActionRunState.Working && state.run === run) state.copy(progress = progress) else state }
	}

	private suspend fun answer(run: ActionRun, from: ActionRunState.Page?, reply: ActionReply) {
		val page = reply.page
		val open = reply.open
		reply.message?.let(::showText)
		when {
			reply.errors.isNotEmpty() && from != null -> _state.value = from.copy(errors = pluginErrors(from.page.fields, reply.errors))
			// Called without a page, so there is none to show them on.
			reply.errors.isNotEmpty() -> {
				_state.value = null
				showText(reply.errors.values.filter { it.isNotBlank() }.joinToString("\n"))
			}
			open != null -> {
				_state.value = null
				if (!opened(open)) message(Res.string.plugin_action_open_missing)
			}
			page != null -> show(ActionRunState.Page(run, page, JsonObject(page.fields.associate { it.key to it.default })))
			// Ends without a word unless the plugin gave one.
			else -> _state.value = null
		}
	}

	private suspend fun opened(target: ActionTarget): Boolean = try {
		(target.place == ActionPlace.Project || target.id != null) && navigate(target.place, target.id)
	} catch (e: CancellationException) {
		throw e
	} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
		Napier.e(e) { "Could not open ${target.place.id} ${target.id} for a plugin action" }
		false
	}

	private fun show(page: ActionRunState.Page) {
		_state.value = page
		if (page.page.fields.any { it is ActionField.Scenes }) loadScenes()
	}

	private fun showText(text: String) {
		if (text.isBlank()) return
		showMessage(if (text.length > MAX_MESSAGE) text.take(MAX_MESSAGE).trimEnd() + "…" else text)
	}

	private fun message(text: StringResource) {
		scope.launch { showMessage(strings.get(text)) }
	}

	/** The first page's values: the last ones given it, where its fields still accept them, otherwise the defaults. */
	private fun firstValues(action: PluginAction): JsonObject {
		val last = remembered[action.key]
		return JsonObject(
			action.fields.associate { field ->
				field.key to (last?.get(field.key)?.takeIf { field.accepts(it) } ?: field.default)
			}
		)
	}

	// Loaded afresh for each page, since scenes come and go while the project is open.
	private fun loadScenes() {
		_scenes.value = null
		scope.launch {
			_scenes.value = try {
				val tree = operations.dispatch(SCENE_TREE, buildJsonObject { put("project", project.name) })
				OperationJson.decodeFromJsonElement(SceneTree.serializer(), tree).nodes.flatMap { it.flatten(depth = 0) }
			} catch (e: CancellationException) {
				throw e
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				Napier.e(e) { "Could not list the scenes to choose from" }
				emptyList()
			}
		}
	}

	private class Stop {
		@Volatile
		var requested = false
	}

	private val PluginAction.key get() = "$pluginId/$name"

	private companion object {
		const val NOTE_CREATE = "note.create"
		const val SCENE_TREE = "scene.tree"

		/** Longer plugin messages are cut, since a snackbar is not the place for a report. */
		const val MAX_MESSAGE = 200
	}
}

/** What the host finds wrong in [values] before a call: a required value missing, a number out of range, or a choice not among the options. */
internal fun hostErrors(fields: List<ActionField>, values: JsonObject): Map<String, FieldError> = fields.mapNotNull { field ->
	val value = values[field.key]
	val error = when (field) {
		is ActionField.Text -> FieldError.Required.takeIf { field.required && (value as? JsonPrimitive)?.contentOrNull.isNullOrBlank() }
		is ActionField.Scenes -> FieldError.Required.takeIf { field.required && sceneIds(value).isEmpty() }
		is ActionField.Setting -> when (val declaration = field.declaration) {
			is SettingDeclaration.Number -> FieldError.OutOfRange(declaration.min, declaration.max).takeUnless { field.accepts(value) }
			is SettingDeclaration.Choice -> FieldError.NotAnOption.takeUnless { field.accepts(value) }
			else -> null
		}
	}
	error?.let { field.key to it }
}.toMap()

/** A plugin's [errors] about a page of [fields]: those not about one of them are put together under "". */
internal fun pluginErrors(fields: List<ActionField>, errors: Map<String, String>): Map<String, FieldError> {
	val keys = fields.mapTo(HashSet()) { it.key }
	val (onFields, onPage) = errors.entries.partition { it.key.isNotEmpty() && it.key in keys }
	val page = onPage.map { it.value }.filter { it.isNotBlank() }.joinToString("\n")
	return onFields.associate { it.key to FieldError.Plugin(it.value) } +
		listOfNotNull(page.takeIf { it.isNotEmpty() }?.let { "" to FieldError.Plugin(it) })
}

/** Whether [value] is one this field can hold; a number's range and a choice's options count. */
private fun ActionField.accepts(value: JsonElement?): Boolean = when (this) {
	is ActionField.Setting -> (value as? JsonPrimitive)?.let(declaration::accept) != null
	is ActionField.Text -> (value as? JsonPrimitive)?.isString == true
	is ActionField.Scenes -> if (multiple) value is JsonArray else value is JsonPrimitive
}

private fun SceneNode.flatten(depth: Int): List<ExportableScene> =
	listOf(ExportableScene(id, name, isGroup = kind == SceneKind.Group, depth = depth)) + children.flatMap { it.flatten(depth + 1) }

/** Whole lines of [markdown] that fit in [limit] characters with a marker after them. */
internal fun cutToFit(markdown: String, limit: Int, marker: String = "\n\n…"): String {
	val room = markdown.take(limit - marker.length)
	val lines = room.substringBeforeLast('\n', missingDelimiterValue = room)
	return lines.trimEnd() + marker
}

/** The selected scene ids in a scenes field's [value]: a list, one id, or nothing. */
internal fun sceneIds(value: JsonElement?): Set<Int> = when (value) {
	is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.toSet()
	is JsonPrimitive -> setOfNotNull(value.content.toIntOrNull())
	else -> emptySet()
}
