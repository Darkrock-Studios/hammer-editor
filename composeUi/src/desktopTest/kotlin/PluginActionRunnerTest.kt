import com.darkrockstudios.apps.hammer.common.compose.plugin.ActionRunState
import com.darkrockstudios.apps.hammer.common.compose.plugin.FieldError
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginActionRunner
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.ActionButton
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCall
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPage
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionProgress
import com.darkrockstudios.apps.hammer.operations.plugin.ActionReply
import com.darkrockstudios.apps.hammer.operations.plugin.ActionTarget
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.TextFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PluginActionRunnerTest {

	private val messages = mutableListOf<String>()
	private val notes = mutableListOf<String>()
	private val calls = mutableListOf<ActionCall>()
	private val opened = mutableListOf<Pair<ActionPlace, Int?>>()

	private val operations = OperationRegistry(
		listOf(
			operation<JsonObject, JsonObject>("note.create", "", Access.Write, OperationScope.Content) { input ->
				notes += input["content"]!!.jsonPrimitive.content
				JsonObject(emptyMap())
			},
		),
		NoProjects,
	)

	// Messages from resources come back as their keys.
	private val strings = object : StrRes {
		override suspend fun get(str: StringResource) = str.key
		override suspend fun get(str: StringResource, vararg args: Any) = str.key
	}

	private fun TestScope.runner(): PluginActionRunner {
		val dispatcher = StandardTestDispatcher(testScheduler)
		return PluginActionRunner(
			project = fakeProjectDef().copy(name = "Storm"),
			scope = this,
			operations = operations,
			strings = strings,
			showMessage = { messages += it },
			// Item 404 is missing.
			navigate = { place, id -> opened += place to id; id != MISSING },
			workDispatcher = dispatcher,
		)
	}

	private fun action(
		fields: List<ActionField> = emptyList(),
		reply: suspend (ActionCall) -> ActionReply,
	) = PluginAction("names", "make", "Make names", setOf(ActionPlace.Project), fields) { call ->
		calls += call
		reply(call)
	}

	private val PluginActionRunner.page get() = assertIs<ActionRunState.Page>(state.value)

	private val count = ActionField.Setting(SettingDeclaration.Number("count", "How many", 5, min = 1, max = 10))
	private val style = ActionField.Setting(
		SettingDeclaration.Choice("style", "Style", "elvish", listOf("elvish", "norse").map { SettingDeclaration.Choice.Option(it, it) })
	)
	private val name = ActionField.Text("name", "Name", required = true)
	private val story = ActionField.Text("story", "Story", format = TextFormat.Rich, defaultValue = "It *began*.")
	private val scenes = ActionField.Scenes("scenes", "Scenes", required = true)

	private val next = ActionButton("next", "Next", primary = true)
	private val back = ActionButton("back", "Back", validate = false)

	@Test
	fun `an action without fields is called at once, and its message is shown`() = runTest {
		val runner = runner()
		runner.start(action { ActionReply(message = "Made 3") }, ActionPlace.Entry, 7)
		advanceUntilIdle()

		assertEquals(listOf("Made 3"), messages)
		assertNull(runner.state.value)
		val call = calls.single()
		assertEquals(ActionPlace.Entry to 7, call.place to call.itemId)
		assertEquals(listOf(null, null), listOf(call.button, call.state))
		assertEquals(JsonObject(emptyMap()), call.input)
	}

	@Test
	fun `an action's fields are its first page, run by Run, with defaults first and the last values after`() = runTest {
		val runner = runner()
		val make = action(listOf(count, story)) { ActionReply() }
		runner.start(make, ActionPlace.Project, null)
		val first = runner.page
		assertTrue(first.first)
		assertEquals(JsonObject(mapOf("count" to JsonPrimitive(5L), "story" to JsonPrimitive("It *began*."))), first.values)
		assertTrue(calls.isEmpty())

		runner.change("count", JsonPrimitive(3))
		runner.press(null)
		advanceUntilIdle()
		assertEquals(JsonPrimitive(3), calls.single().input["count"])
		assertEquals(listOf(null, null), listOf(calls.single().button, calls.single().state))
		// It ended without a message, so nothing is said.
		assertTrue(messages.isEmpty())
		assertNull(runner.state.value)

		runner.start(make, ActionPlace.Project, null)
		assertEquals(JsonPrimitive(3), runner.page.values["count"])
	}

	@Test
	fun `a remembered value the field no longer accepts falls back to its default`() = runTest {
		val runner = runner()
		fun style(vararg values: String) = ActionField.Setting(
			SettingDeclaration.Choice("style", "Style", values.first(), values.map { SettingDeclaration.Choice.Option(it, it) })
		)
		runner.start(action(listOf(style("everyday", "elvish"))) { ActionReply() }, ActionPlace.Project, null)
		runner.change("style", JsonPrimitive("elvish"))
		runner.press(null)
		advanceUntilIdle()

		runner.start(action(listOf(style("everyday", "norse"))) { ActionReply() }, ActionPlace.Project, null)
		assertEquals(JsonPrimitive("everyday"), runner.page.values["style"])
	}

	@Test
	fun `starting another action while one runs says so`() = runTest {
		val runner = runner()
		val never = CompletableDeferred<ActionReply>()
		runner.start(action { never.await() }, ActionPlace.Project, null)
		advanceUntilIdle()
		runner.start(action { ActionReply() }, ActionPlace.Project, null)
		advanceUntilIdle()

		assertEquals(1, calls.size)
		assertEquals(listOf("plugin_action_busy"), messages)
		runner.stop()
	}

	@Test
	fun `the host checks required values, ranges, and options before calling, and an edit clears its error`() = runTest {
		val runner = runner()
		runner.start(action(listOf(name, scenes, count, style)) { ActionReply() }, ActionPlace.Project, null)
		assertEquals(JsonArray(emptyList()), runner.page.values["scenes"])
		runner.change("name", JsonPrimitive("   "))
		runner.change("count", JsonPrimitive(11))
		runner.change("style", JsonPrimitive("orcish"))
		runner.press(null)
		advanceUntilIdle()

		assertTrue(calls.isEmpty())
		assertEquals(
			mapOf(
				"name" to FieldError.Required,
				"scenes" to FieldError.Required,
				"count" to FieldError.OutOfRange(1, 10),
				"style" to FieldError.NotAnOption,
			),
			runner.page.errors,
		)

		runner.change("name", JsonPrimitive("Mara"))
		assertEquals(setOf("scenes", "count", "style"), runner.page.errors.keys)
	}

	@Test
	fun `a button that skips the checks calls with the values as they are`() = runTest {
		val runner = runner()
		runner.start(
			action { call -> if (call.button == null) ActionReply(ActionPage(fields = listOf(name), buttons = listOf(back, next))) else ActionReply() },
			ActionPlace.Project,
			null,
		)
		advanceUntilIdle()

		runner.press(next)
		advanceUntilIdle()
		assertEquals(mapOf("name" to FieldError.Required), runner.page.errors)
		assertEquals(1, calls.size)

		runner.press(back)
		advanceUntilIdle()
		assertEquals("back", calls.last().button)
		assertEquals(JsonObject(mapOf("name" to JsonPrimitive(""))), calls.last().input)
	}

	@Test
	fun `a page replaces the last, starting from its defaults, and its state comes back with the next call`() = runTest {
		val runner = runner()
		val state = JsonObject(mapOf("step" to JsonPrimitive(2)))
		runner.start(
			action { call ->
				when (call.button) {
					null -> ActionReply(ActionPage(title = "Step 2", fields = listOf(story, count), buttons = listOf(next), state = state))
					else -> ActionReply(ActionPage(markdown = "# Done"))
				}
			},
			ActionPlace.Project,
			null,
		)
		advanceUntilIdle()
		val second = runner.page
		assertEquals("Step 2", second.page.title)
		assertEquals(false, second.first)
		assertEquals(JsonObject(mapOf("story" to JsonPrimitive("It *began*."), "count" to JsonPrimitive(5L))), second.values)

		runner.change("story", JsonPrimitive("It *ended*."))
		runner.press(next)
		advanceUntilIdle()
		assertEquals(state, calls.last().state)
		assertEquals(JsonPrimitive("It *ended*."), calls.last().input["story"])
		assertEquals("# Done", runner.page.page.markdown)
		assertEquals(JsonObject(emptyMap()), runner.page.values)
		assertTrue(messages.isEmpty())
	}

	@Test
	fun `errors keep the page, its values, and its state, and those about no field are the page's`() = runTest {
		val runner = runner()
		val state = JsonObject(mapOf("seed" to JsonPrimitive(7)))
		runner.start(
			action { call ->
				when (call.button) {
					null -> ActionReply(ActionPage(fields = listOf(name, count), buttons = listOf(next), state = state))
					else -> ActionReply(
						message = "Not yet",
						errors = mapOf("name" to "Taken", "" to "Try again", "colour" to "No such field"),
						page = ActionPage(markdown = "ignored"),
					)
				}
			},
			ActionPlace.Project,
			null,
		)
		advanceUntilIdle()
		val asked = runner.page
		runner.change("name", JsonPrimitive("Mara"))
		runner.press(next)
		advanceUntilIdle()

		val kept = runner.page
		assertSame(asked.page, kept.page)
		assertEquals(JsonPrimitive("Mara"), kept.values["name"])
		assertEquals(
			mapOf("name" to FieldError.Plugin("Taken"), "" to FieldError.Plugin("Try again\nNo such field")),
			kept.errors,
		)
		assertEquals(listOf("Not yet"), messages)

		runner.press(next)
		advanceUntilIdle()
		assertEquals(state, calls.last().state)
	}

	@Test
	fun `errors with no page to keep are shown, and the run ends`() = runTest {
		val runner = runner()
		runner.start(action { ActionReply(errors = mapOf("" to "Nothing to name")) }, ActionPlace.Project, null)
		advanceUntilIdle()

		assertNull(runner.state.value)
		assertEquals(listOf("Nothing to name"), messages)
	}

	@Test
	fun `open ends the run and shows the item, and a missing one is a failure`() = runTest {
		val runner = runner()
		runner.start(action { ActionReply(message = "Added Mara", open = ActionTarget(ActionPlace.Entry, 12)) }, ActionPlace.Project, null)
		advanceUntilIdle()
		assertNull(runner.state.value)
		assertEquals(listOf<Pair<ActionPlace, Int?>>(ActionPlace.Entry to 12), opened)
		assertEquals(listOf("Added Mara"), messages)

		runner.start(action { ActionReply(open = ActionTarget(ActionPlace.Note, MISSING)) }, ActionPlace.Project, null)
		advanceUntilIdle()
		assertNull(runner.state.value)
		assertEquals(listOf("Added Mara", "plugin_action_open_missing"), messages)

		runner.start(action { ActionReply(open = ActionTarget(ActionPlace.Scene, null)) }, ActionPlace.Project, null)
		advanceUntilIdle()
		assertEquals(listOf("Added Mara", "plugin_action_open_missing", "plugin_action_open_missing"), messages)

		runner.start(action { ActionReply(open = ActionTarget(ActionPlace.Project, null)) }, ActionPlace.Project, null)
		advanceUntilIdle()
		assertEquals(listOf<Pair<ActionPlace, Int?>>(ActionPlace.Entry to 12, ActionPlace.Note to MISSING, ActionPlace.Project to null), opened)
		assertEquals(3, messages.size)
	}

	@Test
	fun `errors win over open and a page, and empty errors are none`() = runTest {
		val runner = runner()
		runner.start(
			action { call ->
				when (call.button) {
					null -> ActionReply(ActionPage(fields = listOf(name), buttons = listOf(next)))
					else -> ActionReply(ActionPage(markdown = "Next"), errors = mapOf("name" to "Taken"), open = ActionTarget(ActionPlace.Entry, 1))
				}
			},
			ActionPlace.Project,
			null,
		)
		advanceUntilIdle()
		val asked = runner.page
		runner.change("name", JsonPrimitive("Mara"))
		runner.press(next)
		advanceUntilIdle()
		assertSame(asked.page, runner.page.page)
		assertTrue(opened.isEmpty())

		runner.dismiss()
		runner.start(action { ActionReply(ActionPage(markdown = "Shown"), errors = emptyMap()) }, ActionPlace.Project, null)
		advanceUntilIdle()
		assertEquals("Shown", runner.page.page.markdown)
	}

	@Test
	fun `a long message is cut`() = runTest {
		val runner = runner()
		runner.start(action { ActionReply(message = "x".repeat(500)) }, ActionPlace.Project, null)
		advanceUntilIdle()

		assertEquals(201, messages.single().length)
		assertTrue(messages.single().endsWith("…"))
	}

	@Test
	fun `a page's markdown is saved as a note, cut at a line break when too long`() = runTest {
		val runner = runner()
		val line = "x".repeat(99)
		runner.start(action { ActionReply(ActionPage(markdown = List(200) { line }.joinToString("\n"))) }, ActionPlace.Project, null)
		advanceUntilIdle()

		runner.saveAsNote()
		advanceUntilIdle()

		val saved = notes.single()
		assertTrue(saved.length <= NotesRepository.MAX_NOTE_SIZE)
		assertTrue(saved.endsWith("$line\n\n…"))
		assertEquals(listOf("plugin_action_saved_cut"), messages)
		assertNull(runner.state.value)
	}

	@Test
	fun `a page with fields is not saved as a note`() = runTest {
		val runner = runner()
		runner.start(action { ActionReply(ActionPage(markdown = "# Names", fields = listOf(name))) }, ActionPlace.Project, null)
		advanceUntilIdle()

		runner.saveAsNote()
		advanceUntilIdle()
		assertTrue(notes.isEmpty())
		runner.page
	}

	@Test
	fun `progress is shown while it runs, over the page it was called from, and stopping ends it`() = runTest {
		val runner = runner()
		val never = CompletableDeferred<ActionReply>()
		runner.start(
			action(listOf(count)) { call ->
				call.onProgress(ActionProgress(0.25f, "Scene 1 of 4"))
				never.await()
			},
			ActionPlace.Project,
			null,
		)
		val first = runner.page
		runner.press(null)
		advanceUntilIdle()
		val working = assertIs<ActionRunState.Working>(runner.state.value)
		assertEquals(0.25f, working.progress?.fraction)
		assertSame(first, working.from)

		runner.stop()
		advanceUntilIdle()
		assertTrue(calls.single().cancelled())
		assertNull(runner.state.value)
		assertEquals(listOf("plugin_action_stopped"), messages)
	}

	@Test
	fun `a page stays editable while its call runs, pressing again does nothing, and what was typed outlives errors`() = runTest {
		val runner = runner()
		val answer = CompletableDeferred<ActionReply>()
		runner.start(
			action { call -> if (call.button == null) ActionReply(ActionPage(fields = listOf(name), buttons = listOf(next))) else answer.await() },
			ActionPlace.Project,
			null,
		)
		advanceUntilIdle()
		runner.change("name", JsonPrimitive("Mara"))
		runner.press(next)
		advanceUntilIdle()
		runner.press(next)
		runner.change("name", JsonPrimitive("Mara Vey"))
		advanceUntilIdle()
		assertEquals(2, calls.size)
		assertEquals(JsonPrimitive("Mara"), calls.last().input["name"])

		answer.complete(ActionReply(errors = mapOf("name" to "Taken")))
		advanceUntilIdle()
		assertEquals(JsonPrimitive("Mara Vey"), runner.page.values["name"])
		assertEquals(mapOf("name" to FieldError.Plugin("Taken")), runner.page.errors)
	}

	@Test
	fun `a stopped call that ends late leaves the next run alone`() = runTest {
		val runner = runner()
		val late = CompletableDeferred<Unit>()
		runner.start(
			action { withContext(NonCancellable) { late.await() }; error("ended late") },
			ActionPlace.Project,
			null,
		)
		advanceUntilIdle()
		runner.stop()
		val never = CompletableDeferred<ActionReply>()
		val next = action { never.await() }
		runner.start(next, ActionPlace.Project, null)
		late.complete(Unit)
		advanceUntilIdle()

		assertSame(next, assertIs<ActionRunState.Working>(runner.state.value).run.action)
		assertEquals(listOf("plugin_action_stopped"), messages)
		runner.stop()
	}

	@Test
	fun `Run is only the first page's`() = runTest {
		val runner = runner()
		runner.start(action { ActionReply(ActionPage(fields = listOf(story), buttons = listOf(next))) }, ActionPlace.Project, null)
		advanceUntilIdle()
		runner.press(null)
		advanceUntilIdle()
		assertEquals(1, calls.size)
	}

	@Test
	fun `a long message is not cut between the halves of a character`() = runTest {
		val runner = runner()
		runner.start(action { ActionReply(message = "x".repeat(199) + "\uD83D\uDE00" + "x".repeat(50)) }, ActionPlace.Project, null)
		advanceUntilIdle()

		assertEquals("x".repeat(199) + "…", messages.single())
	}

	@Test
	fun `a failure is reported`() = runTest {
		val runner = runner()
		runner.start(action { error("broken") }, ActionPlace.Project, null)
		advanceUntilIdle()

		assertNull(runner.state.value)
		assertEquals(listOf("plugin_action_failed"), messages)
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}

	private companion object {
		const val MISSING = 404
	}
}
