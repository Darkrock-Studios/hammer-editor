package com.darkrockstudios.apps.hammer.common.compose.plugin

import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.EntityCatalog
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.find
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.composeui.resources.Res
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_busy
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_failed
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_keep_failed
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_keep_set_aside
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_keep_unreadable
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_no
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_open_missing
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_save_failed
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_saved
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_saved_cut
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_stopped
import com.darkrockstudios.apps.hammer.composeui.resources.plugin_action_yes
import com.darkrockstudios.apps.hammer.operations.OperationJson
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.core.SceneKind
import com.darkrockstudios.apps.hammer.operations.core.SceneNode
import com.darkrockstudios.apps.hammer.operations.core.SceneTree
import com.darkrockstudios.apps.hammer.operations.plugin.ActionAnswer
import com.darkrockstudios.apps.hammer.operations.plugin.ActionButton
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCall
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCancelledException
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPage
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionProgress
import com.darkrockstudios.apps.hammer.operations.plugin.ActionReply
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSession
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionException
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionKey
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionStore
import com.darkrockstudios.apps.hammer.operations.plugin.ActionTarget
import com.darkrockstudios.apps.hammer.operations.plugin.EntityRenumbering
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.UnreadableSessionException
import com.darkrockstudios.apps.hammer.operations.plugin.entityRefs
import com.darkrockstudios.apps.hammer.operations.plugin.toCatalogRef
import io.github.aakira.napier.Napier
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.StringResource
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/** One run of an action, from opening it to its end: the action, and where it was started. */
class ActionRun(val action: PluginAction, val place: ActionPlace, itemId: Int?) {
	/** The item it was run from; sync may give it a new id part way through. */
	var itemId: Int? = itemId
		internal set

	/** What was submitted on each field so far, by [answerKey], for a kept action; never sent to the plugin. */
	internal var answers: Map<String, ActionAnswer> = emptyMap()

	/** Whether the user has been told a save failed, which they are only once a run. */
	@Volatile
	internal var saveFailureShown = false
}

/** What is wrong with a field's value: the plugin's word on it, or a check the host makes before calling. */
sealed interface FieldError {
	data class Plugin(val message: String) : FieldError

	/** A required text left blank, or required scenes or entities left unpicked. */
	data object Required : FieldError

	data class OutOfRange(val min: Long?, val max: Long?) : FieldError

	data object NotAnOption : FieldError

	/** An entity picked that is no longer in the project. */
	data object Missing : FieldError

	/** An entity picked that is not of a kind, entry type, or tag the field takes. */
	data object NotTaken : FieldError
}

/** What the runner is showing. */
sealed interface ActionRunState {
	val run: ActionRun

	/**
	 * A [page], with the [values] of its fields so far and the [errors] in them by field key; the key
	 * "" is about the whole page. The [first] page is the action's own fields, run by the host's Run.
	 * A [resumed] page was restored from a kept run: it offers Start over, and the next call from it
	 * says it resumed.
	 */
	data class Page(
		override val run: ActionRun,
		val page: ActionPage,
		val values: JsonObject,
		val errors: Map<String, FieldError> = emptyMap(),
		val first: Boolean = false,
		val saving: Boolean = false,
		val resumed: Boolean = false,
	) : ActionRunState

	/**
	 * A run that cannot carry on, since its state may mean nothing now: the [answers] entered in it, to
	 * copy or save before starting afresh. Another version of the plugin saved it, or, when
	 * [renumbered], sync gave an item a new id that its state may hold by the old one.
	 */
	data class Changed(
		override val run: ActionRun,
		val answers: Map<String, ActionAnswer>,
		val saving: Boolean = false,
		val renumbered: Boolean = false,
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
 * each action's first page for next time. A kept action's run is saved in [sessions] as the user
 * goes, until it ends, and picked up where it was left when the action is opened again.
 */
class PluginActionRunner(
	private val project: ProjectDef,
	private val scope: CoroutineScope,
	private val operations: OperationRegistry,
	/** The project's items for entity fields to pick from. */
	private val entities: EntityCatalog,
	private val strings: StrRes,
	private val showMessage: (String) -> Unit,
	/** Shows the project's home, or the item at a place with an id; false when there is no such item. */
	private val navigate: suspend (ActionPlace, Int?) -> Boolean,
	sessions: ActionSessionStore,
	private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
	/** Where kept runs are read and written. */
	private val sessionDispatcher: CoroutineContext = Dispatchers.Default,
) {
	private val _state = MutableStateFlow<ActionRunState?>(null)
	val state: StateFlow<ActionRunState?> = _state.asStateFlow()

	private val _scenes = MutableStateFlow<List<ExportableScene>?>(null)

	/** The project's scenes for a scenes field, loaded when a page asks for one. */
	val scenes: StateFlow<List<ExportableScene>?> = _scenes.asStateFlow()

	private val _catalog = MutableStateFlow<List<CatalogItem>?>(null)

	/** The project's items of the kinds a page's entity fields take, loaded when it shows; null until then. */
	val catalog: StateFlow<List<CatalogItem>?> = _catalog.asStateFlow()
	private var catalogLoad: Job? = null

	// A press whose entity picks are being checked against the project.
	private var checking: Job? = null

	// First pages' values, for actions that are not kept; a kept one's whole run is saved instead.
	private val remembered = mutableMapOf<String, Remembered>()

	private class Remembered(val fields: List<ActionField>, val values: JsonObject)
	private var job: Job? = null
	private var stop: Stop? = null

	private val writer = ActionSessionWriter(sessions, scope, sessionDispatcher, ::saveFailed)

	// The save waiting for the user to stop typing.
	private var saveSoon: Job? = null

	// A kept action's saved run being read, during which another cannot start.
	private var opening = false

	private val unfollow = sessions.followRenumberings { renumbering ->
		if (renumbering.projectId == project.id) scope.launch { renumbered(renumbering) }
	}

	fun start(action: PluginAction, place: ActionPlace, itemId: Int?) {
		when (_state.value) {
			null -> Unit
			is ActionRunState.Working -> return message(Res.string.plugin_action_busy)
			else -> return
		}
		if (opening) return
		val run = ActionRun(action, place, itemId)
		if (!action.keep) return begin(run)
		opening = true
		scope.launch {
			try {
				val saved = try {
					withContext(sessionDispatcher) { writer.load(key(run)) }
				} catch (e: UnreadableSessionException) {
					// Starting afresh would write over a session left where it was.
					if (e.setAside == null) return@launch message(Res.string.plugin_action_keep_unreadable)
					message(Res.string.plugin_action_keep_set_aside)
					null
				}
				// What a run another version saved has picked is shown by name, so the items are read for it.
				val items = saved?.takeIf { it.pluginVersion != action.pluginVersion }?.page?.fields
					?.takeIf { fields -> fields.any { it is ActionField.Entity } }
					?.let { readCatalog(it) }
				if (_state.value == null) resume(run, saved, items)
			} finally {
				opening = false
			}
		}
	}

	/** Clears the restored run showing and starts its action afresh. The dialog asks first. */
	fun startOver() {
		val page = _state.value as? ActionRunState.Page ?: return
		if (page.resumed) restart(page.run)
	}

	/** Clears a run another version of the plugin saved, and starts its action afresh. */
	fun startAfresh() {
		val changed = _state.value as? ActionRunState.Changed ?: return
		restart(changed.run)
	}

	/**
	 * Saves the kept run showing and writes every save still waiting, on the calling thread. For when
	 * the host leaves, since the scope saves run in leaves with it. The run is saved even with no edit
	 * waiting, in case its last save failed.
	 */
	fun flush() {
		keep()
		writer.flush()
	}

	/** Stops following sync's new ids, for when the host leaves. */
	fun close() = unfollow()

	private fun begin(run: ActionRun) {
		val action = run.action
		if (action.fields.isEmpty()) {
			call(run, from = null, button = null)
		} else {
			show(ActionRunState.Page(run, ActionPage(fields = action.fields), firstValues(action), first = true))
		}
	}

	/**
	 * Shows the run [saved] for [run]'s action where it was left, or what was entered in it if the plugin
	 * has changed since, naming the entities picked from [items].
	 */
	private fun resume(run: ActionRun, saved: ActionSession?, items: List<CatalogItem>?) {
		val page = saved?.page
		when {
			saved == null -> begin(run)
			// Its state may mean nothing to the module now, so it is not called with it.
			page == null || saved.pluginVersion != run.action.pluginVersion || saved.renumbered -> {
				// What is on the page replaces what was submitted to the same question, unless it is blank.
				val onPage = page?.let { answersOf(it.fields, saved.values, items) }.orEmpty().filterValues { !it.value.isBlankText }
				_state.value = ActionRunState.Changed(run, saved.answers + onPage, renumbered = saved.renumbered)
			}
			else -> {
				run.answers = saved.answers
				val values = JsonObject(page.fields.associate { it.key to (saved.values[it.key] ?: it.default) })
				show(ActionRunState.Page(run, page, values, first = saved.first, resumed = true))
			}
		}
	}

	private fun restart(run: ActionRun) {
		forget(run)
		_state.value = null
		begin(ActionRun(run.action, run.place, run.itemId))
	}

	/**
	 * Sets a field of the page showing, clearing any error on it. A page stays editable while a call
	 * made from it runs, and what is typed then is kept if the call answers with errors.
	 */
	fun change(key: String, value: JsonElement) {
		_state.update { state ->
			when (state) {
				is ActionRunState.Page -> state.changed(key, value)
				is ActionRunState.Working -> state.from?.let { state.copy(from = it.changed(key, value)) } ?: state
				is ActionRunState.Changed, null -> state
			}
		}
		if (shownPage()?.run?.action?.keep == true) keepSoon()
	}

	/**
	 * Calls the action with the page's values once they pass the host's checks, which [button] can
	 * skip. A null [button] is the first page's Run.
	 */
	fun press(button: ActionButton?) {
		val page = _state.value as? ActionRunState.Page ?: return
		if (page.saving || (button == null && !page.first)) return
		if (button?.validate == false) {
			// A check still running for another button must not call too, from this page or one it comes back to.
			checking?.cancel()
			return submit(page, button)
		}
		val picks = page.page.fields.any { it is ActionField.Entity && entityRefs(page.values[it.key]).isNotEmpty() }
		if (!picks) return check(page, button, items = null)
		// The picks are checked against the project as it is at the press, not as it was when the page showed.
		if (checking?.isActive == true) return
		checking = scope.launch {
			val items = readCatalog(page.page.fields)
			val now = _state.value as? ActionRunState.Page
			if (now == null || now.page !== page.page) return@launch
			if (items != null) _catalog.value = items
			check(now, button, items)
		}
	}

	/**
	 * Calls with the page's values if they pass the host's checks, and otherwise shows what fails.
	 * Entity picks are looked for in [items], unless that is null.
	 */
	private fun check(page: ActionRunState.Page, button: ActionButton?, items: List<CatalogItem>?) {
		val errors = hostErrors(page.page.fields, page.values, items)
		if (errors.isNotEmpty()) {
			_state.value = page.copy(errors = errors)
			return
		}
		submit(page, button)
	}

	private fun submit(page: ActionRunState.Page, button: ActionButton?) {
		val run = page.run
		if (run.action.keep) {
			run.answers += answersOf(page.page.fields, page.values)
		} else if (page.first) {
			remembered[run.action.key] = Remembered(page.page.fields, page.values)
		}
		call(run, from = page, button = button?.id)
		keep()
	}

	/**
	 * Stops the call in progress at once: cancelling it interrupts the plugin. A kept run goes back to
	 * the page it was called from, as the user left it.
	 */
	fun stop() {
		val working = _state.value as? ActionRunState.Working ?: return
		stop?.requested = true
		job?.cancel()
		back(working)
		message(Res.string.plugin_action_stopped)
	}

	/**
	 * Closes the dialog, unless a call is running. A kept run is saved as it is, to pick up again, unless
	 * its page is [over].
	 */
	fun dismiss() {
		when (val state = _state.value) {
			is ActionRunState.Working -> return
			is ActionRunState.Page -> if (state.over) forget(state.run) else keep()
			is ActionRunState.Changed, null -> Unit
		}
		_state.value = null
	}

	/**
	 * Saves the markdown of a [document][isDocument] page as a note, cut at a line break if it is too
	 * long for one, and closes it; or, for a run the plugin has changed since, what was entered in it.
	 */
	fun saveAsNote() {
		when (val showing = _state.value) {
			is ActionRunState.Page -> saveAsNote(showing)
			is ActionRunState.Changed -> saveAsNote(showing)
			else -> Unit
		}
	}

	private fun saveAsNote(showing: ActionRunState.Page) {
		// Closing a page with buttons would end a run that has further to go.
		if (!showing.page.isDocument) return
		val markdown = showing.page.markdown?.trim() ?: return
		// A second press while the first save runs would make a second note.
		if (showing.saving) return
		_state.value = showing.copy(saving = true)
		scope.launch {
			val saved = saveNote(markdown)
			val state = _state.value
			if (state !is ActionRunState.Page || state.page !== showing.page) return@launch
			if (saved) {
				_state.value = null
				forget(state.run)
			} else {
				_state.value = state.copy(saving = false)
			}
		}
	}

	// It stays showing, to be started afresh.
	private fun saveAsNote(showing: ActionRunState.Changed) {
		if (showing.saving || showing.answers.isEmpty()) return
		_state.value = showing.copy(saving = true)
		scope.launch {
			val markdown = answersMarkdown(showing.answers.values, strings.get(Res.string.plugin_action_yes), strings.get(Res.string.plugin_action_no))
			if (markdown.isNotEmpty()) saveNote(markdown)
			_state.update { state -> if (state is ActionRunState.Changed && state.run === showing.run) state.copy(saving = false) else state }
		}
	}

	/** Saves [markdown] as a note, saying how that went; false when it failed. */
	private suspend fun saveNote(markdown: String): Boolean {
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
		message(
			when {
				!saved -> Res.string.plugin_action_save_failed
				fits -> Res.string.plugin_action_saved
				else -> Res.string.plugin_action_saved_cut
			}
		)
		return saved
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
							resumed = from?.resumed == true,
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
				calling(stop)?.let { working ->
					back(working)
					message(Res.string.plugin_action_failed)
				}
				return@launch
			}
			// Once stopped, the screen has moved on; an action that ignores being cancelled can still get here.
			val working = calling(stop) ?: return@launch
			answer(run, working.from, reply)
		}
	}

	/**
	 * Ends a call that did not finish: a kept run goes back to the page it was called from, with what
	 * was typed and its state as they were, and anything else ends.
	 */
	private fun back(working: ActionRunState.Working) {
		val from = working.from
		if (working.run.action.keep && from != null) {
			_state.value = from
			keep()
		} else {
			_state.value = null
		}
	}

	/**
	 * The state while the call [stop] belongs to is the one running, or null once it is not. A reply
	 * that comes after that changes nothing, kept runs included.
	 */
	private fun calling(stop: Stop): ActionRunState.Working? = (_state.value as? ActionRunState.Working)?.takeIf { this.stop === stop }

	private fun report(run: ActionRun, progress: ActionProgress) {
		_state.update { state -> if (state is ActionRunState.Working && state.run === run) state.copy(progress = progress) else state }
	}

	private suspend fun answer(run: ActionRun, from: ActionRunState.Page?, reply: ActionReply) {
		val page = reply.page
		val open = reply.open
		reply.message?.let(::showText)
		when {
			reply.errors.isNotEmpty() && from != null -> {
				_state.value = from.copy(errors = pluginErrors(from.page.fields, reply.errors), resumed = false)
				keep()
			}
			// Called without a page, so there is none to show them on.
			reply.errors.isNotEmpty() -> {
				_state.value = null
				showText(reply.errors.values.filter { it.isNotBlank() }.joinToString("\n"))
			}
			open != null -> {
				_state.value = null
				forget(run)
				if (!opened(open)) message(Res.string.plugin_action_open_missing)
			}
			page != null -> {
				show(ActionRunState.Page(run, page, JsonObject(page.fields.associate { it.key to it.default })))
				keep()
			}
			// Ends without a word unless the plugin gave one.
			else -> {
				_state.value = null
				forget(run)
			}
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
		if (page.page.fields.any { it is ActionField.Entity }) loadCatalog(page.page)
	}

	private fun showText(text: String) {
		if (text.isBlank()) return
		if (text.length <= MAX_MESSAGE) return showMessage(text)
		// Not between the halves of a surrogate pair.
		val cut = if (text[MAX_MESSAGE - 1].isHighSurrogate()) MAX_MESSAGE - 1 else MAX_MESSAGE
		showMessage(text.take(cut).trimEnd() + "…")
	}

	private fun message(text: StringResource) {
		scope.launch { showMessage(strings.get(text)) }
	}

	/** The page showing, or the one a call in progress was made from. */
	private fun shownPage(): ActionRunState.Page? = when (val state = _state.value) {
		is ActionRunState.Page -> state
		is ActionRunState.Working -> state.from
		is ActionRunState.Changed, null -> null
	}

	/** Saves the kept run showing once the user has stopped typing for a moment. */
	private fun keepSoon() {
		saveSoon?.cancel()
		saveSoon = scope.launch {
			delay(SAVE_AFTER)
			saveSoon = null
			keep()
		}
	}

	/** Saves the kept run showing, as it is now. */
	private fun keep() {
		saveSoon?.cancel()
		saveSoon = null
		val page = shownPage() ?: return
		val run = page.run
		if (!run.action.keep) return
		// The first page as it opened holds nothing to keep, and closing it should not leave a run to resume.
		val untouched = page.first && !page.resumed && run.answers.isEmpty() &&
			page.page.fields.all { page.values[it.key] == it.default }
		if (untouched) return writer.remove(run, key(run))
		writer.save(run, key(run), ActionSession(run.action.pluginVersion, page.page, page.values, page.first, run.answers))
	}

	/**
	 * Follows sync giving an item a new id: in first pages' remembered values, and in the run showing,
	 * whose kept session the store has followed already. A page whose state may hold the item by its old
	 * id, which now names another, is not carried on but shown as entered. A call in progress is left to
	 * answer as it will.
	 */
	private fun renumbered(renumbering: EntityRenumbering) {
		remembered.entries.forEach { it.setValue(Remembered(it.value.fields, renumbering.values(it.value.fields, it.value.values))) }
		val shown = shownPage() ?: return
		val run = shown.run
		val itemId = renumbering.itemId(run.place, run.itemId)
		if (itemId != run.itemId) {
			// A save of it still waiting would put it back under the old id, which is now another item's.
			if (run.action.keep) writer.remove(run, key(run))
			run.itemId = itemId
		}
		val page = shown.copy(page = renumbering.page(shown.page), values = renumbering.values(shown.page.fields, shown.values))
		when (val state = _state.value) {
			is ActionRunState.Page -> if (renumbering.mentioned(page.page.state)) {
				val onPage = answersOf(page.page.fields, page.values).filterValues { !it.value.isBlankText }
				_state.value = ActionRunState.Changed(run, run.answers + onPage, renumbered = true)
				if (run.action.keep) {
					writer.save(run, key(run), ActionSession(run.action.pluginVersion, page.page, page.values, page.first, run.answers, renumbered = true))
				}
				return
			} else {
				_state.value = page
			}

			is ActionRunState.Working -> _state.value = state.copy(from = page)
			is ActionRunState.Changed, null -> Unit
		}
		keep()
	}

	/** Removes a kept [run], which has ended. */
	private fun forget(run: ActionRun) {
		saveSoon?.cancel()
		saveSoon = null
		if (run.action.keep) writer.remove(run, key(run))
	}

	private fun saveFailed(run: ActionRun, e: ActionSessionException) {
		Napier.e(e) { "Could not save a run of plugin action '${run.action.name}'" }
		if (run.saveFailureShown) return
		run.saveFailureShown = true
		message(Res.string.plugin_action_keep_failed)
	}

	private fun key(run: ActionRun) = ActionSessionKey(run.action.pluginId, project.id, run.action.name, run.place, run.itemId)

	/**
	 * What the user has given [fields], as answers to keep by [answerKey]: choices by their option's
	 * label, and scenes and entities by name where known, entities from [items].
	 */
	private fun answersOf(
		fields: List<ActionField>,
		values: JsonObject,
		items: List<CatalogItem>? = _catalog.value,
	): Map<String, ActionAnswer> = fields.associate { field ->
		val value = values[field.key] ?: field.default
		val answer = when (field) {
			is ActionField.Text -> JsonPrimitive((value as? JsonPrimitive)?.contentOrNull.orEmpty())
			is ActionField.Scenes -> {
				val scenes = _scenes.value.orEmpty()
				JsonPrimitive(sceneIds(value).joinToString(", ") { id -> scenes.firstOrNull { it.id == id }?.name ?: id.toString() })
			}
			is ActionField.Entity -> {
				val known = items.orEmpty()
				JsonPrimitive(entityRefs(value).joinToString(", ") { ref -> known.find(ref)?.name ?: "${ref.kind.id} ${ref.id}" })
			}
			is ActionField.Setting -> {
				val declaration = field.declaration
				val primitive = value as? JsonPrimitive ?: declaration.default
				if (declaration is SettingDeclaration.Choice) {
					JsonPrimitive(declaration.options.firstOrNull { it.value == primitive.contentOrNull }?.label ?: primitive.content)
				} else {
					primitive
				}
			}
		}
		field.answerKey to ActionAnswer(field.label, answer)
	}

	/** The first page's values: the last ones given it, where its fields still accept them, otherwise the defaults. */
	private fun firstValues(action: PluginAction): JsonObject {
		val last = remembered[action.key]?.takeUnless { action.keep }?.values
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

	/**
	 * Loaded afresh for each page, as scenes are. Once it is, a pick on [page] that is no longer in the
	 * project, or that its field does not take, is dropped: a plugin's default naming a deleted entry,
	 * say, shows as nothing picked rather than as an error.
	 */
	private fun loadCatalog(page: ActionPage) {
		catalogLoad?.cancel()
		_catalog.value = null
		catalogLoad = scope.launch {
			val items = readCatalog(page.fields)
			_catalog.value = items.orEmpty()
			if (items == null) return@launch
			_state.update { state ->
				when (state) {
					is ActionRunState.Page -> if (state.page === page) state.pruned(items) else state
					is ActionRunState.Working -> state.from?.takeIf { it.page === page }?.let { state.copy(from = it.pruned(items)) } ?: state
					is ActionRunState.Changed, null -> state
				}
			}
		}
	}

	/** The items of every kind [fields]' entity fields take; null when they could not be read. */
	private suspend fun readCatalog(fields: List<ActionField>): List<CatalogItem>? {
		val kinds = fields.filterIsInstance<ActionField.Entity>().flatMapTo(HashSet()) { it.filter.kinds }
		return try {
			withContext(workDispatcher) { entities.items(kinds) }
		} catch (e: CancellationException) {
			throw e
		} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
			Napier.e(e) { "Could not list the project's items to choose from" }
			null
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

		/** How long after the last edit a kept run is saved. */
		val SAVE_AFTER = 500.milliseconds
	}
}

/** [answers] to read or copy: each label bolded, its value under it. Blank answers are left out. */
internal fun answersMarkdown(answers: Collection<ActionAnswer>, yes: String, no: String): String = answers.mapNotNull { answer ->
	val value = answer.value
	if (value.isBlankText) return@mapNotNull null
	val text = if (!value.isString && value.booleanOrNull != null) (if (value.boolean) yes else no) else value.content.trim()
	"**${answer.label}**\n\n$text"
}.joinToString("\n\n")

private val JsonPrimitive.isBlankText: Boolean get() = isString && content.isBlank()

/**
 * Pages may use a key again for another question, as an interview does when its set changes, so an
 * answer is kept by its label as well and neither replaces the other.
 */
internal val ActionField.answerKey: String get() = "$key\u0000$label"

/**
 * Whether this page leaves nothing to do but read it: no field to fill in and no button to go on with.
 * A kept run on it ends when it is closed, and until then it is kept like any other.
 */
internal val ActionRunState.Page.over: Boolean get() = !first && page.fields.isEmpty() && page.buttons.isEmpty()

private fun ActionRunState.Page.changed(key: String, value: JsonElement) =
	copy(values = JsonObject(values + (key to value)), errors = errors - key)

/** This page with only the entity picks [items] has and their fields take. */
private fun ActionRunState.Page.pruned(items: List<CatalogItem>): ActionRunState.Page {
	val kept = page.fields.filterIsInstance<ActionField.Entity>().mapNotNull { field ->
		val refs = entityRefs(values[field.key])
		val taken = refs.filter { ref -> items.find(ref)?.let(field.filter::accepts) == true }
		(field.key to field.valueOf(taken)).takeIf { taken.size != refs.size }
	}
	return if (kept.isEmpty()) this else copy(values = JsonObject(values + kept))
}

/** Whether a page only shows markdown: nothing to fill in, and no button to go on with. */
internal val ActionPage.isDocument: Boolean get() = markdown != null && fields.isEmpty() && buttons.isEmpty()

/**
 * What the host finds wrong in [values] before a call: a required value missing, a number out of
 * range, a choice not among the options, or an entity picked that is not among [items] or that its
 * field does not take. Entities are not looked for when [items] is null.
 */
internal fun hostErrors(fields: List<ActionField>, values: JsonObject, items: List<CatalogItem>? = null): Map<String, FieldError> = fields.mapNotNull { field ->
	val value = values[field.key]
	val error = when (field) {
		is ActionField.Text -> FieldError.Required.takeIf { field.required && (value as? JsonPrimitive)?.contentOrNull.isNullOrBlank() }
		is ActionField.Scenes -> FieldError.Required.takeIf { field.required && sceneIds(value).isEmpty() }
		is ActionField.Entity -> {
			val refs = entityRefs(value)
			val found = items?.let { refs.mapNotNull { ref -> it.find(ref) } }
			when {
				refs.isEmpty() -> FieldError.Required.takeIf { field.required }
				found == null -> null
				found.size < refs.size -> FieldError.Missing
				found.any { !field.filter.accepts(it) } -> FieldError.NotTaken
				else -> null
			}
		}
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
	is ActionField.Entity -> if (multiple) value is JsonArray && value.all { it.toCatalogRef() != null } else value == JsonNull || value?.toCatalogRef() != null
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
