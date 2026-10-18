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
import com.darkrockstudios.apps.hammer.operations.plugin.ActionButton
import com.darkrockstudios.apps.hammer.operations.plugin.ActionField
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPage
import com.darkrockstudios.apps.hammer.operations.plugin.ActionPlace
import com.darkrockstudios.apps.hammer.operations.plugin.ActionReply
import com.darkrockstudios.apps.hammer.operations.plugin.PluginAction
import com.darkrockstudios.apps.hammer.operations.plugin.TextFormat
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.module
import java.util.Collections
import kotlin.test.assertEquals
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
			KoinApplicationPreview(application = { modules(module { single { OperationRegistry(emptyList(), NoProjects) } }) }) {
				PluginActionHost(fakeProjectDef(), emptySet(), rememberRootSnackbarHostState(), navigate = { _, _ -> true }) {
					val runner = LocalPluginActions.current!!
					LaunchedEffect(Unit) { runner.start(interview, ActionPlace.Project, null) }
				}
			}
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

	private object NoProjects : ProjectResolver {
		override fun resolve(project: String): ProjectDef = error("unused")
		override suspend fun <T> withProject(project: String, block: suspend (OpenProject) -> T): T = error("unused")
	}
}
