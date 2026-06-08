import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkrockstudios.apps.hammer.android.ProjectRootActivity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.notes.NOTES_CREATE_BODY_TAG
import com.darkrockstudios.apps.hammer.common.notes.NOTES_CREATE_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.notes.NOTES_CREATE_FAB_TAG
import com.darkrockstudios.apps.hammer.common.notes.NOTES_CREATE_META_TAG
import com.darkrockstudios.apps.hammer.common.projectroot.NAV_NOTES_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Notes happy path: navigate to notes, create a note (body required) via the FAB + create screen,
 * which returns to the browse list, then confirm the new note card appears and open it.
 */
@RunWith(AndroidJUnit4::class)
class NotesWorkflowTest {

	@get:Rule
	val composeRule = createEmptyComposeRule()

	private lateinit var projectDef: ProjectDef
	private lateinit var scenario: ActivityScenario<ProjectRootActivity>

	@Before
	fun setup() {
		projectDef = EditorTestHarness.seedProject("E2E Notes")
		scenario = EditorTestHarness.launchEditor(projectDef)
	}

	@Test
	fun createNoteThenOpenIt() {
		composeRule.navigateTo(NAV_NOTES_TAG)

		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(NOTES_CREATE_FAB_TAG).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag(NOTES_CREATE_FAB_TAG).performClick()

		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(NOTES_CREATE_BODY_TAG).fetchSemanticsNodes().isNotEmpty()
		}
		val emptyMeta = composeRule.textOf(NOTES_CREATE_META_TAG)
		composeRule.typeIntoEditor(NOTES_CREATE_BODY_TAG, "E2E smoke note body")

		// The editor reports text changes through an async flow, so the body can still be
		// empty right after typing. Creating an empty note silently no-ops (stays on this
		// screen), so wait for the word/char counter to change before confirming.
		composeRule.waitUntil(10_000) {
			composeRule.textOf(NOTES_CREATE_META_TAG) != emptyMeta
		}
		composeRule.onNodeWithTag(NOTES_CREATE_CONFIRM_TAG).performClick()

		// Creating returns to the browse grid; the new note card appears (id unknown, match by prefix).
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodes(hasTestTagPrefix("note-card-")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onAllNodes(hasTestTagPrefix("note-card-")).onFirst().assertIsDisplayed()

		// Opening it leaves the browse list - the create FAB only shows there.
		composeRule.onAllNodes(hasTestTagPrefix("note-card-")).onFirst().performClick()
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(NOTES_CREATE_FAB_TAG).fetchSemanticsNodes().isEmpty()
		}
	}

	@After
	fun tearDown() {
		EditorTestHarness.teardown(scenario, projectDef)
	}
}
