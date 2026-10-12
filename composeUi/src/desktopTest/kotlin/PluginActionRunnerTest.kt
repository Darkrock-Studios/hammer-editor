import com.darkrockstudios.apps.hammer.common.compose.plugin.ActionRunState
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginActionRunner
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.notesrepository.NotesRepository
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
import com.darkrockstudios.apps.hammer.operations.plugin.ActionOutput
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionProgress
import com.darkrockstudios.apps.hammer.operations.plugin.ActionReply
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.SettingDeclaration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.StringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginActionRunnerTest {

	private val messages = mutableListOf<String>()
	private val notes = mutableListOf<String>()
	private val calls = mutableListOf<ActionCall>()

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
		return PluginActionRunner("Storm", this, operations, strings, { messages += it }, dispatcher)
	}

	private fun action(
		fields: List<ActionField> = emptyList(),
		reply: suspend (ActionCall) -> ActionReply,
	) = PluginAction("names", "make", "Make names", setOf(ActionPlace.Project), fields, ActionOutput.Interactive) { call ->
		calls += call
		reply(call)
	}

	private val count = ActionField.Setting(SettingDeclaration.Number("count", "How many", 5))
	private val scenes = ActionField.Scenes("scenes", "Scenes", required = true)

	@Test
	fun `an action without fields runs at once, and its message is shown`() = runTest {
		val runner = runner()
		runner.start(action { ActionReply(message = "Made 3") }, ActionPlace.Entry, 7)
		advanceUntilIdle()

		assertEquals(listOf("Made 3"), messages)
		assertNull(runner.state.value)
		assertEquals(ActionPlace.Entry to 7, calls.single().place to calls.single().itemId)
	}

	@Test
	fun `fields are asked for, with defaults first and the last values after`() = runTest {
		val runner = runner()
		val make = action(listOf(count)) { ActionReply() }
		runner.start(make, ActionPlace.Project, null)
		assertEquals(JsonObject(mapOf("count" to JsonPrimitive(5))), assertIs<ActionRunState.Asking>(runner.state.value).values)

		runner.change("count", JsonPrimitive(3))
		runner.submit()
		advanceUntilIdle()
		assertEquals(JsonPrimitive(3), calls.single().input["count"])
		assertEquals(listOf("plugin_action_done"), messages)

		runner.start(make, ActionPlace.Project, null)
		assertEquals(JsonPrimitive(3), assertIs<ActionRunState.Asking>(runner.state.value).values["count"])
	}

	@Test
	fun `a remembered value the field no longer accepts falls back to its default`() = runTest {
		val runner = runner()
		fun style(vararg values: String) = ActionField.Setting(
			SettingDeclaration.Choice("style", "Style", values.first(), values.map { SettingDeclaration.Choice.Option(it, it) })
		)
		runner.start(action(listOf(style("everyday", "elvish"))) { ActionReply() }, ActionPlace.Project, null)
		runner.change("style", JsonPrimitive("elvish"))
		runner.submit()
		advanceUntilIdle()

		runner.start(action(listOf(style("everyday", "norse"))) { ActionReply() }, ActionPlace.Project, null)
		assertEquals(JsonPrimitive("everyday"), assertIs<ActionRunState.Asking>(runner.state.value).values["style"])
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
	fun `a scenes field starts empty`() = runTest {
		val runner = runner()
		runner.start(action(listOf(scenes)) { ActionReply() }, ActionPlace.Project, null)
		assertEquals(JsonArray(emptyList()), assertIs<ActionRunState.Asking>(runner.state.value).values["scenes"])
	}

	@Test
	fun `markdown is shown with its buttons, which call the action again`() = runTest {
		val runner = runner()
		runner.start(
			action { call ->
				if (call.button == null) ActionReply(markdown = "# Names", buttons = listOf(ActionButton("a", "Aldric")))
				else ActionReply(message = "Created ${call.button}")
			},
			ActionPlace.Project,
			null,
		)
		advanceUntilIdle()
		val showing = assertIs<ActionRunState.Showing>(runner.state.value)
		assertEquals("# Names", showing.reply.markdown)

		runner.press(showing.reply.buttons.single())
		advanceUntilIdle()
		assertEquals(listOf(null, "a"), calls.map { it.button })
		assertEquals(listOf("Created a"), messages)
		assertNull(runner.state.value)
	}

	@Test
	fun `shown markdown is saved as a note, cut at a line break when too long`() = runTest {
		val runner = runner()
		val line = "x".repeat(99)
		runner.start(action { ActionReply(markdown = List(200) { line }.joinToString("\n")) }, ActionPlace.Project, null)
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
	fun `progress is shown while it runs, and stopping ends it`() = runTest {
		val runner = runner()
		val never = CompletableDeferred<ActionReply>()
		runner.start(
			action { call ->
				call.onProgress(ActionProgress(0.25f, "Scene 1 of 4"))
				never.await()
			},
			ActionPlace.Project,
			null,
		)
		advanceUntilIdle()
		assertEquals(0.25f, assertIs<ActionRunState.Working>(runner.state.value).progress?.fraction)

		runner.stop()
		advanceUntilIdle()
		assertTrue(calls.single().cancelled())
		assertNull(runner.state.value)
		assertEquals(listOf("plugin_action_stopped"), messages)
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
}
