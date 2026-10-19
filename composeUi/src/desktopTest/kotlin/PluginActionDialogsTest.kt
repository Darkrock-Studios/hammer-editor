import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.plugin.LocalPluginActions
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginActionHost
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogFilter
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.EntityCatalog
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.operations.OpenProject
import com.darkrockstudios.apps.hammer.operations.OperationRegistry
import com.darkrockstudios.apps.hammer.operations.ProjectResolver
import com.darkrockstudios.apps.hammer.operations.plugin.ActionAnswer
import com.darkrockstudios.apps.hammer.operations.plugin.ActionButton
import com.darkrockstudios.apps.hammer.operations.plugin.ActionCall
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPage
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionReply
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSession
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionKey
import com.darkrockstudios.apps.hammer.operations.plugin.ActionSessionStore
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.TextFormat
import com.darkrockstudios.apps.hammer.operations.plugin.toJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.module
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginActionDialogsTest {

	@get:Rule
	val compose = createComposeRule()

	private val buttons: MutableList<String?> = Collections.synchronizedList(mutableListOf())

	private val interview = PluginAction("people", "interview", "Interview", setOf(ActionPlace.Project), emptyList()) { call ->
		buttons += call.button
		ActionReply(
			ActionPage(
				title = "Mara",
				markdown = "Tell me about **Mara**.",
				fields = listOf(
					ActionField.Text("want", "Wants", hint = "What she wants most", format = TextFormat.Rich, defaultValue = "To go home."),
					ActionField.Text("nickname", "Nickname"),
				),
				buttons = listOf(ActionButton("back", "Back", validate = false), ActionButton("next", "Next", primary = true)),
			)
		)
	}

	@Test
	fun `a page shows its markdown, fields, and buttons, with room for rich text in the scrolling dialog`() {
		compose.setContent {
			Host(interview)
		}
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("What she wants most").fetchSemanticsNodes().isNotEmpty() }

		compose.onNodeWithText("Tell me about", substring = true).assertExists()
		compose.onNodeWithText("Nickname", ignoreCase = true).assertExists()
		val label = compose.onNodeWithText("Wants", ignoreCase = true).getBoundsInRoot()
		val hint = compose.onNodeWithText("What she wants most").getBoundsInRoot()
		// The editor sits between them: its format bar and at least three lines.
		assertTrue(hint.top - label.bottom > 60.dp, "${hint.top - label.bottom}")

		compose.onNodeWithText("Next", ignoreCase = true).performClick()
		compose.waitUntil(timeoutMillis = 10_000) { buttons.size == 2 }
		assertEquals(listOf(null, "next"), buttons.toList())
	}

	private val store = ActionSessionStore(FakeFileSystem(), "/config/plugins".toPath())
	private val key = ActionSessionKey("people", fakeProjectDef().id, "ask", ActionPlace.Project, null)
	private val calls: MutableList<ActionCall> = Collections.synchronizedList(mutableListOf())

	private fun kept(version: String) = PluginAction(
		"people", "ask", "Ask", setOf(ActionPlace.Project), listOf(ActionField.Text("name", "Name")), keep = true, pluginVersion = version,
	) { call ->
		calls += call
		ActionReply()
	}

	private fun saveSession(version: String) = store.save(
		key,
		ActionSession(
			pluginVersion = version,
			page = ActionPage(title = "Who is it?", fields = listOf(ActionField.Text("name", "Name"))),
			values = JsonObject(mapOf("name" to JsonPrimitive("Mara"))),
			first = false,
			answers = mapOf("set" to ActionAnswer("Question set", JsonPrimitive("Proust"))),
		),
	)

	@Composable
	private fun Host(action: PluginAction) {
		KoinApplicationPreview(
			application = {
				modules(
					module {
						single { OperationRegistry(emptyList(), NoProjects) }
						single { store }
						scope<ProjectDefScope> { scoped<EntityCatalog> { catalog } }
					}
				)
			},
		) {
			PluginActionHost(fakeProjectDef(), emptySet(), rememberRootSnackbarHostState(), navigate = { _, _ -> true }) {
				val runner = LocalPluginActions.current!!
				LaunchedEffect(Unit) { runner.start(action, ActionPlace.Project, null) }
			}
		}
	}

	@Test
	fun `a page of markdown with buttons offers Copy but not Save as note, which would end its run`() {
		val review = PluginAction("people", "review", "Review", setOf(ActionPlace.Project), emptyList()) {
			ActionReply(ActionPage(markdown = "## Mara", buttons = listOf(ActionButton("back", "Back"), ActionButton("create", "Create entry", primary = true))))
		}
		compose.setContent { Host(review) }
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Create entry", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }

		compose.onNodeWithText("Copy", ignoreCase = true).assertExists()
		compose.onNodeWithText("Save as note", ignoreCase = true).assertDoesNotExist()
	}

	@Test
	fun `a resumed page offers Start over, which asks before clearing the run`() {
		saveSession("1.0.0")
		compose.setContent { Host(kept("1.0.0")) }
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Start over", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
		compose.onNodeWithText("Mara").assertExists()

		compose.onNodeWithText("Start over", ignoreCase = true).performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Start over?").fetchSemanticsNodes().isNotEmpty() }
		assertNotNull(store.load(key))
		compose.onNodeWithText("Keep going").performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Start over?").fetchSemanticsNodes().isEmpty() }
		assertNotNull(store.load(key))

		compose.onNodeWithText("Start over", ignoreCase = true).performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Start over?").fetchSemanticsNodes().isNotEmpty() }
		// The footer's button, then the dialog's.
		compose.onAllNodesWithText("Start over", ignoreCase = true)[1].performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Mara").fetchSemanticsNodes().isEmpty() }
		compose.waitUntil(timeoutMillis = 10_000) { store.load(key) == null }
		assertTrue(calls.isEmpty())
	}

	@Test
	fun `a run another version saved shows what was entered, and Start afresh asks before clearing it`() {
		saveSession("1.0.0")
		compose.setContent { Host(kept("2.0.0")) }
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Start afresh", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
		compose.onNodeWithText("Question set", substring = true).assertExists()
		compose.onNodeWithText("Proust", substring = true).assertExists()
		compose.onNodeWithText("Mara", substring = true).assertExists()

		compose.onNodeWithText("Start afresh", ignoreCase = true).performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Start afresh?").fetchSemanticsNodes().isNotEmpty() }
		compose.onNodeWithText("Keep going").performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Start afresh?").fetchSemanticsNodes().isEmpty() }
		assertNotNull(store.load(key))

		compose.onNodeWithText("Start afresh", ignoreCase = true).performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Start afresh?").fetchSemanticsNodes().isNotEmpty() }
		// The footer's button, then the dialog's.
		compose.onAllNodesWithText("Start afresh", ignoreCase = true)[1].performClick()
		compose.waitUntil(timeoutMillis = 10_000) { store.load(key) == null }
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Run", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
		assertTrue(calls.isEmpty())
	}

	private val alice = CatalogItem(CatalogKind.Entry, 7, "Alice", aliases = listOf("Alys"), entryType = EntryType.PERSON, tags = setOf("pov"))
	private val rabbit = CatalogItem(CatalogKind.Entry, 6, "White Rabbit", entryType = EntryType.PERSON)
	private val wonderland = CatalogItem(CatalogKind.Entry, 12, "Wonderland", entryType = EntryType.PLACE)
	private val garden = CatalogItem(CatalogKind.Scene, 1, "The Garden", detail = "Part One", tags = setOf("pov"))

	private val catalog = object : EntityCatalog {
		override suspend fun items(kinds: Set<CatalogKind>) = listOf(alice, rabbit, wonderland, garden).filter { it.kind in kinds }
	}

	@Test
	fun `an entity field is chosen by name in the picker, one closing it or several with Done, and a pick removed by its chip`() {
		val cast = PluginAction(
			"people", "cast", "Cast", setOf(ActionPlace.Project),
			listOf(
				ActionField.Entity("lead", "Lead", filter = CatalogFilter(setOf(CatalogKind.Entry), setOf(EntryType.PERSON)), required = true),
				ActionField.Entity("pov", "Points of view", filter = CatalogFilter(setOf(CatalogKind.Entry, CatalogKind.Scene), tags = setOf("pov")), multiple = true),
			),
		) { call ->
			calls += call
			ActionReply()
		}
		compose.setContent { Host(cast) }
		// Choose waits for the project's items.
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText("Choose", ignoreCase = true) and isEnabled()).fetchSemanticsNodes().size == 2 }

		// One person: typing narrows by name or alias, and picking closes the picker.
		compose.onAllNodesWithText("Choose", ignoreCase = true)[0].performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(isToggleable() and hasText("White Rabbit", substring = true)).fetchSemanticsNodes().isNotEmpty() }
		compose.onNode(isToggleable() and hasText("Wonderland", substring = true)).assertDoesNotExist()
		compose.onNode(hasSetTextAction()).performTextInput("alys")
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(isToggleable() and hasText("White Rabbit", substring = true)).fetchSemanticsNodes().isEmpty() }
		compose.onNode(isToggleable() and hasText("Alice", substring = true)).performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(isToggleable()).fetchSemanticsNodes().isEmpty() }
		compose.onNodeWithContentDescription("Remove Alice").assertExists()

		// Several, across a tab for each kind, until Done.
		compose.onAllNodesWithText("Choose", ignoreCase = true)[1].performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(isToggleable() and hasText("The Garden", substring = true)).fetchSemanticsNodes().isNotEmpty() }
		compose.onNode(isToggleable() and hasText("The Garden", substring = true)).performClick()
		compose.onNodeWithText("Encyclopedia").performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(isToggleable() and hasText("Alice", substring = true)).fetchSemanticsNodes().isNotEmpty() }
		compose.onNode(isToggleable() and hasText("White Rabbit", substring = true)).assertDoesNotExist()
		compose.onNode(isToggleable() and hasText("Alice", substring = true)).performClick()
		compose.onNodeWithText("Done", ignoreCase = true).performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(isToggleable()).fetchSemanticsNodes().isEmpty() }

		compose.onNodeWithContentDescription("Remove The Garden").performClick()
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithContentDescription("Remove The Garden").fetchSemanticsNodes().isEmpty() }
		compose.onNodeWithText("Run", ignoreCase = true).performClick()
		compose.waitUntil(timeoutMillis = 10_000) { calls.isNotEmpty() }
		assertEquals(alice.ref.toJson(), calls.single().input["lead"])
		assertEquals(JsonArray(listOf(alice.ref.toJson())), calls.single().input["pov"])
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
