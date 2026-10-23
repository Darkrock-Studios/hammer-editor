import com.darkrockstudios.apps.hammer.common.compose.plugin.ActionRunState
import com.darkrockstudios.apps.hammer.common.compose.plugin.FieldError
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginActionRunner
import com.darkrockstudios.apps.hammer.common.compose.plugin.answersMarkdown
import com.darkrockstudios.apps.hammer.common.compose.plugin.hostErrors
import com.darkrockstudios.apps.hammer.base.http.EntityType
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogFilter
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogRef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.EntityCatalog
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.operations.Access
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.OperationScope
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.operation
import com.darkrockstudios.apps.hammer.operations.plugin.ActionAnswer
import com.darkrockstudios.apps.hammer.operations.plugin.ActionButton
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCall
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPage
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionProgress
import com.darkrockstudios.apps.hammer.operations.plugin.ActionReply
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionKey
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionStore
import com.darkrockstudios.apps.hammer.operations.plugin.ActionTarget
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import com.darkrockstudios.apps.hammer.operations.plugin.TextFormat
import com.darkrockstudios.apps.hammer.operations.plugin.toJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

	private val fileSystem = FakeFileSystem()
	private val pluginsDir = "/config/plugins".toPath()
	private val store = ActionSessionStore(fileSystem, pluginsDir)
	private val project = fakeProjectDef().copy(name = "Storm")

	private val alice = CatalogItem(CatalogKind.Entry, 7, "Alice", entryType = EntryType.PERSON, tags = setOf("pov"))
	private val rabbit = CatalogItem(CatalogKind.Entry, 6, "White Rabbit", entryType = EntryType.PERSON)
	private val wonderland = CatalogItem(CatalogKind.Entry, 12, "Wonderland", entryType = EntryType.PLACE, tags = setOf("pov"))
	private val garden = CatalogItem(CatalogKind.Scene, 1, "The Garden", tags = setOf("pov"))

	// What the project holds now; a test changes it to delete an item.
	private var catalogItems = listOf(alice, rabbit, wonderland, garden)
	private val catalogReads = mutableListOf<Set<CatalogKind>>()
	private val catalog = object : EntityCatalog {
		override suspend fun items(kinds: Set<CatalogKind>): List<CatalogItem> {
			catalogReads += kinds
			return catalogItems.filter { it.kind in kinds }
		}
	}

	private fun TestScope.runner(sessions: ActionSessionStore = store): PluginActionRunner {
		val dispatcher = StandardTestDispatcher(testScheduler)
		return PluginActionRunner(
			project = project,
			scope = this,
			operations = operations,
			entities = catalog,
			strings = strings,
			showMessage = { messages += it },
			// Item 404 is missing.
			navigate = { place, id -> opened += place to id; id != MISSING },
			sessions = sessions,
			workDispatcher = dispatcher,
			sessionDispatcher = dispatcher,
		)
	}

	private fun action(
		fields: List<ActionField> = emptyList(),
		keep: Boolean = false,
		version: String = "1.0.0",
		reply: suspend (ActionCall) -> ActionReply,
	) = PluginAction("names", "make", "Make names", setOf(ActionPlace.Project), fields, keep = keep, pluginVersion = version) { call ->
		calls += call
		reply(call)
	}

	private fun kept(version: String = "1.0.0", reply: suspend (ActionCall) -> ActionReply) = action(keep = true, version = version, reply = reply)

	private val key = ActionSessionKey("names", project.id, "make", ActionPlace.Project, null)

	private val saved get() = store.load(key)

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

	// A person, required; and any number of items tagged pov, scenes or entries, required.
	private val who = ActionField.Entity("who", "Who", filter = CatalogFilter(setOf(CatalogKind.Entry), setOf(EntryType.PERSON)), required = true)
	private val cast = ActionField.Entity(
		"cast", "Cast", filter = CatalogFilter(setOf(CatalogKind.Entry, CatalogKind.Scene), tags = setOf("pov")), multiple = true, required = true,
	)

	private fun picks(vararg items: CatalogItem): JsonElement = JsonArray(items.map { it.ref.toJson() })

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
	fun `the host checks entity picks are given when required, still there, and of what their field takes`() {
		fun errors(who: JsonElement, cast: JsonElement, items: List<CatalogItem>? = catalogItems) =
			hostErrors(listOf(this.who, this.cast), JsonObject(mapOf("who" to who, "cast" to cast)), items)
		val deleted = CatalogRef(CatalogKind.Entry, 99).toJson()

		assertEquals(mapOf("who" to FieldError.Required, "cast" to FieldError.Required), errors(JsonNull, JsonArray(emptyList())))
		assertEquals(emptyMap(), errors(alice.ref.toJson(), picks(alice, garden)))
		assertEquals(
			mapOf("who" to FieldError.Missing, "cast" to FieldError.Missing),
			errors(deleted, JsonArray(listOf(alice.ref.toJson(), deleted))),
		)
		// The wrong kind, the wrong entry type, and without the tag.
		assertEquals(mapOf("who" to FieldError.NotTaken), errors(garden.ref.toJson(), picks(wonderland)))
		assertEquals(mapOf("who" to FieldError.NotTaken), errors(wonderland.ref.toJson(), picks(alice)))
		assertEquals(mapOf("cast" to FieldError.NotTaken), errors(rabbit.ref.toJson(), picks(alice, rabbit)))
		// Without the project's items, only what is required is checked.
		assertEquals(emptyMap(), errors(deleted, JsonArray(listOf(deleted)), items = null))
		assertEquals(mapOf("who" to FieldError.Required), errors(JsonNull, JsonArray(listOf(deleted)), items = null))
	}

	@Test
	fun `a page's entity fields load the items they take, and drop defaults no longer there or that they do not take`() = runTest {
		val runner = runner()
		val whoFrom = ActionField.Entity("who", "Who", filter = who.filter, defaultRefs = listOf(garden.ref))
		val castFrom = ActionField.Entity("cast", "Cast", filter = cast.filter, multiple = true, defaultRefs = listOf(alice.ref, CatalogRef(CatalogKind.Entry, 99)))
		runner.start(action(listOf(whoFrom, castFrom)) { ActionReply() }, ActionPlace.Project, null)
		assertEquals(JsonArray(listOf(alice.ref.toJson(), CatalogRef(CatalogKind.Entry, 99).toJson())), runner.page.values["cast"])
		assertNull(runner.catalog.value)

		advanceUntilIdle()
		assertEquals(listOf(setOf(CatalogKind.Entry, CatalogKind.Scene)), catalogReads)
		assertEquals(listOf(alice, rabbit, wonderland, garden), runner.catalog.value)
		assertEquals(JsonNull, runner.page.values["who"])
		assertEquals(picks(alice), runner.page.values["cast"])
		assertTrue(runner.page.errors.isEmpty())
	}

	@Test
	fun `a press checks entity picks against the project as it is then`() = runTest {
		val runner = runner()
		runner.start(action(listOf(who, cast)) { ActionReply() }, ActionPlace.Project, null)
		advanceUntilIdle()
		runner.change("who", rabbit.ref.toJson())
		runner.change("cast", picks(alice, garden))

		catalogItems = catalogItems - rabbit
		runner.press(null)
		advanceUntilIdle()
		assertTrue(calls.isEmpty())
		assertEquals(mapOf("who" to FieldError.Missing), runner.page.errors)

		runner.change("who", alice.ref.toJson())
		runner.press(null)
		advanceUntilIdle()
		assertEquals(JsonObject(mapOf("who" to alice.ref.toJson(), "cast" to picks(alice, garden))), calls.single().input)
	}

	@Test
	fun `a kept run's answers name the entities picked, and its picks come back when it resumes`() = runTest {
		val action = kept { call ->
			if (call.button == null) ActionReply(ActionPage(title = "Who", fields = listOf(who, cast), buttons = listOf(next))) else ActionReply(ActionPage(title = "Done", buttons = listOf(back)))
		}
		val first = runner()
		first.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		first.change("who", alice.ref.toJson())
		first.change("cast", picks(garden, alice))
		first.dismiss()
		advanceUntilIdle()

		val second = runner()
		second.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		assertTrue(second.page.resumed)
		assertEquals(alice.ref.toJson(), second.page.values["who"])
		assertEquals(picks(garden, alice), second.page.values["cast"])

		second.press(next)
		advanceUntilIdle()
		assertEquals("Done", second.page.page.title)
		assertEquals(
			listOf("Who" to "Alice", "Cast" to "The Garden, Alice"),
			saved?.answers?.values?.map { it.label to it.value.content },
		)
	}

	@Test
	fun `a run saved by another version names the entities left picked on its page`() = runTest {
		val first = runner()
		first.start(kept { ActionReply(ActionPage(title = "Who", fields = listOf(who, cast), buttons = listOf(next))) }, ActionPlace.Project, null)
		advanceUntilIdle()
		first.change("who", alice.ref.toJson())
		first.change("cast", picks(garden, alice))
		first.dismiss()
		advanceUntilIdle()

		val second = runner()
		second.start(kept("2.0.0") { ActionReply() }, ActionPlace.Project, null)
		advanceUntilIdle()
		val changed = assertIs<ActionRunState.Changed>(second.state.value)
		assertEquals(listOf("Who" to "Alice", "Cast" to "The Garden, Alice"), changed.answers.values.map { it.label to it.value.content })
	}

	@Test
	fun `a press that skips the checks stops a check still running for another`() = runTest {
		var release = CompletableDeferred<Unit>()
		val slow = object : EntityCatalog {
			override suspend fun items(kinds: Set<CatalogKind>): List<CatalogItem> {
				release.await()
				return catalogItems.filter { it.kind in kinds }
			}
		}
		val dispatcher = StandardTestDispatcher(testScheduler)
		val runner = PluginActionRunner(
			project, this, operations, slow, strings, showMessage = { messages += it }, navigate = { _, _ -> true },
			sessions = store, workDispatcher = dispatcher, sessionDispatcher = dispatcher,
		)
		val page = ActionPage(fields = listOf(who), buttons = listOf(next, back))
		runner.start(action { call -> if (call.button == "back") ActionReply(errors = mapOf("" to "Not yet")) else ActionReply(page) }, ActionPlace.Project, null)
		advanceUntilIdle()
		release.complete(Unit)
		advanceUntilIdle()
		runner.change("who", alice.ref.toJson())

		release = CompletableDeferred()
		runner.press(next)
		runCurrent()
		// Back comes back to the same page with its errors while Next's check is still reading.
		runner.press(back)
		advanceUntilIdle()
		assertEquals(mapOf("" to FieldError.Plugin("Not yet")), runner.page.errors)
		release.complete(Unit)
		advanceUntilIdle()
		assertEquals(listOf(null, "back"), calls.map { it.button })
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
	fun `a page with buttons is not saved as a note, so its run goes on`() = runTest {
		val runner = runner()
		runner.start(action { ActionReply(ActionPage(markdown = "## Mara", buttons = listOf(back, next))) }, ActionPlace.Project, null)
		advanceUntilIdle()

		runner.saveAsNote()
		advanceUntilIdle()
		assertTrue(notes.isEmpty())
		assertEquals("## Mara", runner.page.page.markdown)
	}

	@Test
	fun `a kept run whose page leaves nothing to do is kept until that page is closed`() = runTest {
		val action = interview { ActionReply(ActionPage(markdown = "## Mara\n\nShe *left*.")) }
		val first = runner()
		first.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		first.press(next.copyWithoutChecks())
		advanceUntilIdle()
		first.press(next)
		advanceUntilIdle()
		assertEquals("## Mara\n\nShe *left*.", saved?.page?.markdown)

		// Leaving with it showing, as when the window closes, keeps it.
		first.flush()
		val second = runner()
		second.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		assertTrue(second.page.resumed)
		second.dismiss()
		advanceUntilIdle()
		assertNull(saved)

		val third = runner()
		third.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		third.press(next.copyWithoutChecks())
		advanceUntilIdle()
		third.press(next)
		advanceUntilIdle()
		third.saveAsNote()
		advanceUntilIdle()
		assertEquals("## Mara\n\nShe *left*.", notes.single())
		assertNull(third.state.value)
		assertNull(saved)
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

	// A kept interview: its first call asks for a name, Next asks for a story, and Next again ends it.
	private val interviewState = JsonObject(mapOf("step" to JsonPrimitive(1)))
	private val storyState = JsonObject(mapOf("step" to JsonPrimitive(2)))

	private fun interview(version: String = "1.0.0", last: suspend (ActionCall) -> ActionReply = { ActionReply() }) = kept(version) { call ->
		when {
			call.state == null || call.button == "back" ->
				ActionReply(ActionPage(title = "Who", fields = listOf(name, style), buttons = listOf(next), state = interviewState))
			call.state == interviewState ->
				ActionReply(ActionPage(title = "Story", fields = listOf(story), buttons = listOf(back, next), state = storyState))
			else -> last(call)
		}
	}

	@Test
	fun `a kept run is saved as each page shows, and resumed where it was left, saying so on the next call only`() = runTest {
		val first = runner()
		val action = interview()
		first.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		assertEquals("Who", saved?.page?.title)
		assertEquals(interviewState, saved?.page?.state)

		first.change("name", JsonPrimitive("Mara"))
		first.press(next)
		advanceUntilIdle()
		assertEquals("Story", saved?.page?.title)
		assertEquals(listOf("Name", "Style"), saved?.answers?.values?.map { it.label })
		first.change("story", JsonPrimitive("She *left*."))
		first.dismiss()
		advanceUntilIdle()
		assertNull(first.state.value)
		assertEquals(JsonPrimitive("She *left*."), saved?.values?.get("story"))

		val second = runner()
		second.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		val resumed = second.page
		assertTrue(resumed.resumed)
		assertEquals("Story", resumed.page.title)
		assertEquals(JsonPrimitive("She *left*."), resumed.values["story"])
		assertEquals(2, calls.size)

		second.press(back)
		advanceUntilIdle()
		assertTrue(calls.last().resumed)
		assertEquals(storyState, calls.last().state)
		assertEquals(JsonPrimitive("She *left*."), calls.last().input["story"])
		assertEquals("Who", second.page.page.title)
		assertEquals(false, second.page.resumed)

		second.press(next.copyWithoutChecks())
		advanceUntilIdle()
		assertEquals(4, calls.size)
		assertEquals(false, calls.last().resumed)
	}

	@Test
	fun `typing is saved half a second after it stops`() = runTest {
		val runner = runner()
		runner.start(interview(), ActionPlace.Project, null)
		advanceUntilIdle()

		runner.change("name", JsonPrimitive("Ma"))
		advanceTimeBy(400)
		runCurrent()
		runner.change("name", JsonPrimitive("Mara"))
		advanceTimeBy(400)
		runCurrent()
		assertEquals(JsonPrimitive(""), saved?.values?.get("name"))

		advanceTimeBy(101)
		runCurrent()
		assertEquals(JsonPrimitive("Mara"), saved?.values?.get("name"))
	}

	@Test
	fun `a kept action's first page is saved once typed into, not before, and resumes with Run`() = runTest {
		val action = action(listOf(name), keep = true) { ActionReply(ActionPage(markdown = "Thanks")) }
		val first = runner()
		first.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		assertNull(saved)

		first.change("name", JsonPrimitive("Mara"))
		advanceUntilIdle()
		assertEquals(true, saved?.first)
		first.dismiss()

		val second = runner()
		second.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		assertTrue(second.page.first)
		assertTrue(second.page.resumed)
		assertEquals(JsonPrimitive("Mara"), second.page.values["name"])

		second.press(null)
		advanceUntilIdle()
		assertTrue(calls.single().resumed)
		assertEquals(null, calls.single().button)
	}

	@Test
	fun `a kept action's first page closed as it opened leaves nothing to resume`() = runTest {
		val action = action(listOf(name, count), keep = true) { ActionReply() }
		val runner = runner()
		runner.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		runner.dismiss()
		advanceUntilIdle()
		assertNull(saved)

		// Typed, saved, then cleared again.
		runner.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		runner.change("name", JsonPrimitive("Mara"))
		advanceUntilIdle()
		assertNotNull(saved)
		runner.change("name", JsonPrimitive(""))
		runner.dismiss()
		advanceUntilIdle()
		assertNull(saved)

		runner.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		assertEquals(false, runner.page.resumed)
	}

	@Test
	fun `values a kept action's first page was given are not remembered for its next run`() = runTest {
		val runner = runner()
		val action = action(listOf(count), keep = true) { ActionReply() }
		runner.start(action, ActionPlace.Project, null)
		runner.change("count", JsonPrimitive(3))
		runner.press(null)
		advanceUntilIdle()
		assertNull(saved)

		runner.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		assertEquals(JsonPrimitive(5L), runner.page.values["count"])
	}

	@Test
	fun `Start over clears a resumed run and starts it afresh`() = runTest {
		val action = interview()
		val first = runner()
		first.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		first.change("name", JsonPrimitive("Mara"))
		first.dismiss()
		advanceUntilIdle()

		val second = runner()
		second.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		assertEquals(JsonPrimitive("Mara"), second.page.values["name"])
		second.startOver()
		advanceUntilIdle()

		assertEquals(2, calls.size)
		assertNull(calls.last().state)
		assertEquals(false, calls.last().resumed)
		assertEquals(false, second.page.resumed)
		assertEquals(JsonPrimitive(""), second.page.values["name"])
		assertEquals(JsonPrimitive(""), saved?.values?.get("name"))

		// Only a resumed page starts over.
		second.startOver()
		advanceUntilIdle()
		assertEquals(2, calls.size)
	}

	@Test
	fun `a run saved by another version shows what was entered, and Start afresh clears it`() = runTest {
		val first = runner()
		first.start(interview("1.0.0"), ActionPlace.Project, null)
		advanceUntilIdle()
		first.change("name", JsonPrimitive("Mara"))
		first.change("style", JsonPrimitive("norse"))
		first.press(next)
		advanceUntilIdle()
		first.change("story", JsonPrimitive("She *left*."))
		first.dismiss()
		advanceUntilIdle()

		val second = runner()
		val updated = interview("2.0.0")
		second.start(updated, ActionPlace.Project, null)
		advanceUntilIdle()
		val changed = assertIs<ActionRunState.Changed>(second.state.value)
		assertEquals(2, calls.size)
		assertEquals(
			"**Name**\n\nMara\n\n**Style**\n\nnorse\n\n**Story**\n\nShe *left*.",
			answersMarkdown(changed.answers.values, "Yes", "No"),
		)

		second.saveAsNote()
		advanceUntilIdle()
		assertEquals("**Name**\n\nMara\n\n**Style**\n\nnorse\n\n**Story**\n\nShe *left*.", notes.single())
		assertIs<ActionRunState.Changed>(second.state.value)

		// Closing leaves it for next time.
		second.dismiss()
		assertNotNull(saved)
		second.start(updated, ActionPlace.Project, null)
		advanceUntilIdle()
		second.startAfresh()
		advanceUntilIdle()
		assertEquals(3, calls.size)
		assertNull(calls.last().state)
		assertEquals(false, calls.last().resumed)
		assertEquals("Who", second.page.page.title)
		assertEquals("2.0.0", saved?.pluginVersion)
	}

	@Test
	fun `answers to different questions under the same key are all kept`() = runTest {
		val quick = ActionField.Text("q1", "What do you want?")
		val full = ActionField.Text("q1", "Where were you born?")
		val action = kept { call ->
			val field = if (call.button == "full") full else quick
			ActionReply(ActionPage(fields = listOf(field), buttons = listOf(ActionButton("full", "Full")), state = JsonPrimitive(1)))
		}
		val first = runner()
		first.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		first.change("q1", JsonPrimitive("Home."))
		first.press(ActionButton("full", "Full"))
		advanceUntilIdle()
		first.change("q1", JsonPrimitive("At sea."))
		first.press(ActionButton("full", "Full"))
		advanceUntilIdle()
		first.dismiss()
		advanceUntilIdle()

		val second = runner()
		second.start(kept("2.0.0") { ActionReply() }, ActionPlace.Project, null)
		advanceUntilIdle()
		val changed = assertIs<ActionRunState.Changed>(second.state.value)
		assertEquals(
			"**What do you want?**\n\nHome.\n\n**Where were you born?**\n\nAt sea.",
			answersMarkdown(changed.answers.values, "Yes", "No"),
		)
	}

	@Test
	fun `a saved run that cannot be read is set aside and the action starts afresh, never writing over it`() = runTest {
		val file = pluginsDir / ActionSessionStore.SESSIONS_DIRECTORY / "names" / project.id.id / "make.project.json"
		fileSystem.createDirectories(file.parent!!)
		fileSystem.write(file) { writeUtf8("{\"format\": 9}") }

		val runner = runner()
		runner.start(interview(), ActionPlace.Project, null)
		advanceUntilIdle()
		assertEquals(listOf("plugin_action_keep_set_aside"), messages)
		assertEquals(false, runner.page.resumed)
		runner.change("name", JsonPrimitive("Mara"))
		runner.dismiss()
		advanceUntilIdle()

		assertEquals(JsonPrimitive("Mara"), saved?.values?.get("name"))
		assertEquals("{\"format\": 9}", fileSystem.read(file.parent!! / "make.project.json.unreadable") { readUtf8() })
	}

	@Test
	fun `a saved run that can be neither read nor set aside is not started over`() = runTest {
		val file = pluginsDir / ActionSessionStore.SESSIONS_DIRECTORY / "names" / project.id.id / "make.project.json"
		fileSystem.createDirectories(file.parent!!)
		fileSystem.write(file) { writeUtf8("not json") }
		val stuck = object : ForwardingFileSystem(fileSystem) {
			override fun atomicMove(source: Path, target: Path) = throw IOException("read-only")
		}

		val runner = runner(ActionSessionStore(stuck, pluginsDir))
		runner.start(interview(), ActionPlace.Project, null)
		advanceUntilIdle()
		assertEquals(listOf("plugin_action_keep_unreadable"), messages)
		assertNull(runner.state.value)
		assertTrue(calls.isEmpty())
		assertEquals("not json", fileSystem.read(file) { readUtf8() })
	}

	@Test
	fun `typing during a call that ends the run does not bring its session back`() = runTest {
		val gate = CompletableDeferred<Unit>()
		val runner = runner()
		runner.start(
			interview {
				gate.await()
				ActionReply()
			},
			ActionPlace.Project,
			null,
		)
		advanceUntilIdle()
		runner.press(next.copyWithoutChecks())
		advanceUntilIdle()
		runner.press(next)
		runCurrent()
		runner.change("story", JsonPrimitive("She *left*."))
		gate.complete(Unit)
		advanceUntilIdle()

		assertNull(runner.state.value)
		assertNull(saved)
	}

	@Test
	fun `answers show choices by their label and booleans in words, leaving out blanks`() {
		val answers = listOf(
			ActionAnswer("Brave", JsonPrimitive(true)),
			ActionAnswer("Afraid", JsonPrimitive(false)),
			ActionAnswer("Age", JsonPrimitive(31)),
			ActionAnswer("Nickname", JsonPrimitive("  ")),
			ActionAnswer("Said", JsonPrimitive("true")),
		)
		assertEquals(
			"**Brave**\n\nYes\n\n**Afraid**\n\nNo\n\n**Age**\n\n31\n\n**Said**\n\ntrue",
			answersMarkdown(answers, "Yes", "No"),
		)
	}

	@Test
	fun `ending a kept run removes it, and so does open`() = runTest {
		val runner = runner()
		runner.start(interview(), ActionPlace.Project, null)
		advanceUntilIdle()
		runner.press(next.copyWithoutChecks())
		advanceUntilIdle()
		assertNotNull(saved)
		runner.press(next)
		advanceUntilIdle()
		assertNull(runner.state.value)
		assertNull(saved)

		runner.start(interview { ActionReply(open = ActionTarget(ActionPlace.Entry, 12)) }, ActionPlace.Project, null)
		advanceUntilIdle()
		runner.press(next.copyWithoutChecks())
		advanceUntilIdle()
		assertNotNull(saved)
		runner.press(next)
		advanceUntilIdle()
		assertNull(saved)
		assertEquals(listOf<Pair<ActionPlace, Int?>>(ActionPlace.Entry to 12), opened)
	}

	@Test
	fun `errors keep a kept run, with what was typed`() = runTest {
		val runner = runner()
		runner.start(interview { ActionReply(errors = mapOf("story" to "Too short")) }, ActionPlace.Project, null)
		advanceUntilIdle()
		runner.press(next.copyWithoutChecks())
		advanceUntilIdle()
		runner.change("story", JsonPrimitive("Hm."))
		runner.press(next)
		advanceUntilIdle()

		assertEquals(mapOf("story" to FieldError.Plugin("Too short")), runner.page.errors)
		assertEquals("Story", saved?.page?.title)
		assertEquals(JsonPrimitive("Hm."), saved?.values?.get("story"))
	}

	@Test
	fun `a failed call keeps a kept run, back on its page with what was typed, still resuming`() = runTest {
		val action = interview { error("broken") }
		val first = runner()
		first.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		first.press(next.copyWithoutChecks())
		advanceUntilIdle()
		first.dismiss()

		val second = runner()
		second.start(action, ActionPlace.Project, null)
		advanceUntilIdle()
		val asked = second.page
		second.change("story", JsonPrimitive("She *left*."))
		second.press(next)
		advanceUntilIdle()

		assertEquals(listOf("plugin_action_failed"), messages)
		val back = second.page
		assertSame(asked.page, back.page)
		assertTrue(back.resumed)
		assertEquals(JsonPrimitive("She *left*."), back.values["story"])
		assertEquals(JsonPrimitive("She *left*."), saved?.values?.get("story"))

		second.press(next)
		advanceUntilIdle()
		assertTrue(calls.last().resumed)
	}

	@Test
	fun `Stop keeps a kept run, back on its page with what was typed while it ran`() = runTest {
		val never = CompletableDeferred<ActionReply>()
		val runner = runner()
		runner.start(interview { never.await() }, ActionPlace.Project, null)
		advanceUntilIdle()
		runner.press(next.copyWithoutChecks())
		advanceUntilIdle()
		runner.change("story", JsonPrimitive("She"))
		runner.press(next)
		advanceUntilIdle()
		runner.change("story", JsonPrimitive("She *left*."))
		runner.stop()
		advanceUntilIdle()

		assertEquals(listOf("plugin_action_stopped"), messages)
		assertEquals("Story", runner.page.page.title)
		assertEquals(JsonPrimitive("She *left*."), runner.page.values["story"])
		assertEquals(JsonPrimitive("She *left*."), saved?.values?.get("story"))
	}

	@Test
	fun `a reply after Stop neither saves nor removes the kept run`() = runTest {
		var gate = CompletableDeferred<Unit>()
		var reply = ActionReply()
		val runner = runner()
		runner.start(
			interview {
				val waiting = gate
				withContext(NonCancellable) { waiting.await() }
				reply
			},
			ActionPlace.Project,
			null,
		)
		advanceUntilIdle()
		runner.press(next.copyWithoutChecks())
		advanceUntilIdle()
		runner.press(next)
		advanceUntilIdle()
		runner.stop()
		advanceUntilIdle()
		gate.complete(Unit)
		advanceUntilIdle()
		assertEquals("Story", runner.page.page.title)
		assertEquals("Story", saved?.page?.title)

		gate = CompletableDeferred()
		reply = ActionReply(ActionPage(title = "Late"))
		runner.press(next)
		advanceUntilIdle()
		runner.stop()
		runner.dismiss()
		advanceUntilIdle()
		gate.complete(Unit)
		advanceUntilIdle()
		assertNull(runner.state.value)
		assertEquals("Story", saved?.page?.title)
	}

	@Test
	fun `flush writes typing not yet saved at once`() = runTest {
		val runner = runner()
		runner.start(interview(), ActionPlace.Project, null)
		advanceUntilIdle()
		runner.change("name", JsonPrimitive("Mara"))

		runner.flush()
		assertEquals(JsonPrimitive("Mara"), saved?.values?.get("name"))
	}

	@Test
	fun `actions that are not kept never touch sessions`() = runTest {
		val runner = runner()
		runner.start(
			action(listOf(name)) { call -> if (call.button == null) ActionReply(ActionPage(fields = listOf(story), buttons = listOf(next))) else error("broken") },
			ActionPlace.Project,
			null,
		)
		runner.change("name", JsonPrimitive("Mara"))
		runner.press(null)
		advanceUntilIdle()
		runner.change("story", JsonPrimitive("She *left*."))
		runner.press(next)
		advanceUntilIdle()
		assertNull(runner.state.value)
		runner.flush()

		assertTrue(!fileSystem.exists(pluginsDir / ActionSessionStore.SESSIONS_DIRECTORY))
		assertTrue(calls.none { it.resumed })
	}

	@Test
	fun `a save that fails is reported once a run`() = runTest {
		val failing = object : ForwardingFileSystem(fileSystem) {
			override fun atomicMove(source: Path, target: Path) = throw IOException("disk full")
		}
		val runner = runner(ActionSessionStore(failing, pluginsDir))
		runner.start(interview(), ActionPlace.Project, null)
		advanceUntilIdle()
		runner.change("name", JsonPrimitive("Mara"))
		advanceUntilIdle()
		runner.dismiss()
		advanceUntilIdle()

		assertEquals(listOf("plugin_action_keep_failed"), messages)
	}

	@Test
	fun `flush tries again a save that failed, though nothing was typed since`() = runTest {
		var full = true
		val flaky = object : ForwardingFileSystem(fileSystem) {
			override fun atomicMove(source: Path, target: Path) = if (full) throw IOException("disk full") else super.atomicMove(source, target)
		}
		val runner = runner(ActionSessionStore(flaky, pluginsDir))
		runner.start(interview(), ActionPlace.Project, null)
		advanceUntilIdle()
		runner.change("name", JsonPrimitive("Mara"))
		advanceUntilIdle()
		assertNull(saved)

		full = false
		runner.flush()
		assertEquals(JsonPrimitive("Mara"), saved?.values?.get("name"))
	}

	@Test
	fun `a first page's remembered picks follow a scene sync renumbered`() = runTest {
		val runner = runner()
		val report = action(listOf(scenes)) { ActionReply() }
		runner.start(report, ActionPlace.Project, null)
		runner.change("scenes", JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2))))
		runner.press(null)
		advanceUntilIdle()

		store.onEntityIdChanged(project, EntityType.Scene, 1, 30)
		advanceUntilIdle()

		runner.start(report, ActionPlace.Project, null)
		assertEquals(JsonArray(listOf(JsonPrimitive(30), JsonPrimitive(2))), runner.page.values["scenes"])
	}

	@Test
	fun `a kept run showing follows its item's new id, and is kept under it`() = runTest {
		val runner = runner()
		val action = kept {
			ActionReply(ActionPage(title = "Who", fields = listOf(who), buttons = listOf(next), state = JsonObject(mapOf("step" to JsonPrimitive(1)))))
		}
		val fromAlice = key.copy(place = ActionPlace.Entry, itemId = 7)
		runner.start(action, ActionPlace.Entry, 7)
		advanceUntilIdle()
		runner.change("who", alice.ref.toJson())
		advanceUntilIdle()

		store.onEntityIdChanged(project, EntityType.EncyclopediaEntry, 7, 50)
		advanceUntilIdle()

		val renumbered = CatalogRef(CatalogKind.Entry, 50).toJson()
		assertEquals(renumbered, runner.page.values["who"])
		assertNull(store.load(fromAlice))
		assertEquals(renumbered, store.load(fromAlice.copy(itemId = 50))?.values?.get("who"))

		runner.press(next.copyWithoutChecks())
		advanceUntilIdle()
		assertEquals(50, calls.last().itemId)
		assertEquals(renumbered, calls.last().input["who"])
	}

	@Test
	fun `a run whose state may hold its item's old id is shown as entered, then and when opened again`() = runTest {
		val runner = runner()
		val action = kept {
			ActionReply(ActionPage(title = "Story", fields = listOf(story), buttons = listOf(next), state = JsonObject(mapOf("about" to JsonPrimitive(7)))))
		}
		runner.start(action, ActionPlace.Entry, 7)
		advanceUntilIdle()
		runner.change("story", JsonPrimitive("She *left*."))

		store.onEntityIdChanged(project, EntityType.EncyclopediaEntry, 7, 50)
		advanceUntilIdle()

		val changed = assertIs<ActionRunState.Changed>(runner.state.value)
		assertTrue(changed.renumbered)
		assertEquals("**Story**\n\nShe *left*.", answersMarkdown(changed.answers.values, "Yes", "No"))

		runner.dismiss()
		val again = runner()
		again.start(action, ActionPlace.Entry, 50)
		advanceUntilIdle()
		assertTrue(assertIs<ActionRunState.Changed>(again.state.value).renumbered)
		assertEquals(1, calls.size)
	}

	private fun ActionButton.copyWithoutChecks() = ActionButton(id, label, primary, inline, validate = false)

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}

	private companion object {
		const val MISSING = 404
	}
}
