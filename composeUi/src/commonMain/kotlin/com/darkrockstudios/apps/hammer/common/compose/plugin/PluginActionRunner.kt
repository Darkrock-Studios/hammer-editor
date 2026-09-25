package com.darkrockstudios.apps.hammer.common.compose.plugin

import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.composeui.resources.Res
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_busy
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_done
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_failed
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
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionProgress
import com.darkrockstudios.apps.hammer.operations.plugin.ActionReply
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.StringResource

/** One run of an action: where it was started, and the input it was given. */
class ActionRun(val action: PluginAction, val place: ActionPlace, val itemId: Int?, val input: JsonObject)

/** What the runner is showing. */
sealed interface ActionRunState {
	val run: ActionRun

	/** The action's fields, with the values chosen so far. */
	data class Asking(override val run: ActionRun, val values: JsonObject) : ActionRunState

	data class Working(override val run: ActionRun, val progress: ActionProgress? = null) : ActionRunState

	/** A reply with markdown, while it is shown. */
	data class Showing(override val run: ActionRun, val reply: ActionReply, val saving: Boolean = false) : ActionRunState
}

/**
 * Runs plugin actions for one open project, one at a time: asks for their fields, shows their progress
 * and what they reply, and remembers the values given each action for next time.
 */
class PluginActionRunner(
	private val project: String,
	private val scope: CoroutineScope,
	private val operations: OperationRegistry,
	private val strings: StrRes,
	private val showMessage: (String) -> Unit,
	private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
	private val _state = MutableStateFlow<ActionRunState?>(null)
	val state: StateFlow<ActionRunState?> = _state.asStateFlow()

	private val _scenes = MutableStateFlow<List<ExportableScene>?>(null)

	/** The project's scenes for a scenes field, loaded when an action asks for one. */
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
		val values = initialValues(action)
		val run = ActionRun(action, place, itemId, values)
		if (action.fields.isEmpty()) {
			launch(run, button = null)
		} else {
			_state.value = ActionRunState.Asking(run, values)
			if (action.fields.any { it is ActionField.Scenes }) loadScenes()
		}
	}

	fun change(key: String, value: JsonElement) {
		_state.update { state ->
			if (state is ActionRunState.Asking) state.copy(values = JsonObject(state.values + (key to value))) else state
		}
	}

	fun submit() {
		val asking = _state.value as? ActionRunState.Asking ?: return
		remembered[asking.run.action.key] = asking.values
		launch(ActionRun(asking.run.action, asking.run.place, asking.run.itemId, asking.values), button = null)
	}

	fun press(button: ActionButton) {
		val showing = _state.value as? ActionRunState.Showing ?: return
		launch(showing.run, button.id)
	}

	/** Stops the run in progress: the plugin at its next progress report, and the wait for it at once. */
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

	/** Saves the shown markdown as a note, cut at a line break if it is too long for one. */
	fun saveAsNote() {
		val showing = _state.value as? ActionRunState.Showing ?: return
		val markdown = showing.reply.markdown?.trim() ?: return
		// A second press while the first save runs would make a second note.
		if (showing.saving) return
		_state.value = showing.copy(saving = true)
		scope.launch {
			val fits = markdown.length <= NotesRepository.MAX_NOTE_SIZE
			val text = if (fits) markdown else cutToFit(markdown, NotesRepository.MAX_NOTE_SIZE)
			val saved = try {
				operations.dispatch(NOTE_CREATE, buildJsonObject {
					put("project", project)
					put("content", text)
				})
				true
			} catch (e: CancellationException) {
				throw e
			} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
				Napier.e(e) { "Could not save an action's document as a note" }
				false
			}
			_state.update { state ->
				if (state is ActionRunState.Showing && state.run === showing.run) (if (saved) null else state.copy(saving = false)) else state
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

	private fun launch(run: ActionRun, button: String?) {
		val stop = Stop().also { this.stop = it }
		_state.value = ActionRunState.Working(run)
		job = scope.launch {
			val reply = try {
				withContext(workDispatcher) {
					run.action.run(
						ActionCall(
							project = project,
							place = run.place,
							itemId = run.itemId,
							input = run.input,
							button = button,
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
			show(run, reply)
		}
	}

	private fun report(run: ActionRun, progress: ActionProgress) {
		_state.update { state -> if (state is ActionRunState.Working && state.run === run) state.copy(progress = progress) else state }
	}

	private suspend fun show(run: ActionRun, reply: ActionReply) {
		_state.value = if (reply.markdown != null) ActionRunState.Showing(run, reply) else null
		val message = reply.message
		when {
			message != null -> showMessage(if (message.length > MAX_MESSAGE) message.take(MAX_MESSAGE).trimEnd() + "…" else message)
			reply.markdown == null -> showMessage(strings.get(Res.string.plugin_action_done))
		}
	}

	private fun message(text: StringResource) {
		scope.launch { showMessage(strings.get(text)) }
	}

	private fun initialValues(action: PluginAction): JsonObject {
		val defaults = action.fields.associate { field ->
			field.key to when (field) {
				is ActionField.Setting -> field.declaration.default
				is ActionField.Scenes -> if (field.multiple) JsonArray(emptyList()) else JsonNull
			}
		}
		val last = remembered[action.key] ?: return JsonObject(defaults)
		// A plugin installed again since may have changed a field, so only values still valid carry over.
		return JsonObject(
			action.fields.associate { field ->
				val value = last[field.key]
				field.key to when (field) {
					is ActionField.Setting -> (value as? JsonPrimitive)?.let(field.declaration::accept)
					is ActionField.Scenes -> value?.takeIf { if (field.multiple) it is JsonArray else it is JsonPrimitive }
				}.let { it ?: defaults.getValue(field.key) }
			}
		)
	}

	// Loaded afresh for each run, since scenes come and go while the project is open.
	private fun loadScenes() {
		_scenes.value = null
		scope.launch {
			_scenes.value = try {
				val tree = operations.dispatch(SCENE_TREE, buildJsonObject { put("project", project) })
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
