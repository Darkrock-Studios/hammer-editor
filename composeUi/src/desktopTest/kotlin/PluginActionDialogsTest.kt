import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.plugin.LocalPluginActions
import com.darkrockstudios.apps.hammer.common.compose.plugin.PluginActionHost
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
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
	fun `a run another version saved shows what was entered, and Start afresh clears it`() {
		saveSession("1.0.0")
		compose.setContent { Host(kept("2.0.0")) }
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Start afresh", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
		compose.onNodeWithText("Question set", substring = true).assertExists()
		compose.onNodeWithText("Proust", substring = true).assertExists()
		compose.onNodeWithText("Mara", substring = true).assertExists()

		compose.onNodeWithText("Start afresh", ignoreCase = true).performClick()
		compose.waitUntil(timeoutMillis = 10_000) { store.load(key) == null }
		compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Run", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
		assertTrue(calls.isEmpty())
	}

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
