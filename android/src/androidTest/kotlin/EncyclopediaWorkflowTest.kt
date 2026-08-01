import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkrockstudios.apps.hammer.android.ProjectRootActivity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENCYCLOPEDIA_CREATE_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENCYCLOPEDIA_CREATE_FAB_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENCYCLOPEDIA_CREATE_NAME_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.ENCYCLOPEDIA_CREATE_TAGS_TAG
import com.darkrockstudios.apps.hammer.common.encyclopedia.encyclopediaTypeCellTag
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryType
import com.darkrockstudios.apps.hammer.common.projectroot.NAV_ENCYCLOPEDIA_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Encyclopedia happy path: navigate to encyclopedia, create an entry (name + type + tag; description
 * optional), see it land in the browse grid, and open it.
 */
@RunWith(AndroidJUnit4::class)
class EncyclopediaWorkflowTest {

	@get:Rule
	val composeRule = createEmptyComposeRule()

	private lateinit var projectDef: ProjectDef
	private lateinit var scenario: ActivityScenario<ProjectRootActivity>

	@Before
	fun setup() {
		projectDef = EditorTestHarness.seedProject("E2E Encyclopedia")
		scenario = EditorTestHarness.launchEditor(projectDef)
	}

	@Test
	fun createEntryThenOpenIt() {
		composeRule.navigateTo(NAV_ENCYCLOPEDIA_TAG)

		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(ENCYCLOPEDIA_CREATE_FAB_TAG).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag(ENCYCLOPEDIA_CREATE_FAB_TAG).performClick()

		// Name is required; type defaults to PERSON, pick PLACE. Trailing comma commits the tag.
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(ENCYCLOPEDIA_CREATE_NAME_TAG).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag(ENCYCLOPEDIA_CREATE_NAME_TAG).performTextInput("Smoke Entry")
		composeRule.onNodeWithTag(encyclopediaTypeCellTag(EntryType.PLACE)).performClick()
		composeRule.onNodeWithTag(ENCYCLOPEDIA_CREATE_TAGS_TAG).performTextInput("hero,")
		composeRule.onNodeWithTag(ENCYCLOPEDIA_CREATE_CONFIRM_TAG).performClick()

		// Back on the browse grid, the new entry card appears (id unknown, match by tag prefix).
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodes(hasTestTagPrefix("encyclopedia-entry-")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onAllNodes(hasTestTagPrefix("encyclopedia-entry-")).onFirst().assertIsDisplayed()

		// Opening it leaves the browse list - the create FAB only shows there.
		composeRule.clickUntil(hasTestTagPrefix("encyclopedia-entry-")) {
			composeRule.onAllNodesWithTag(ENCYCLOPEDIA_CREATE_FAB_TAG).fetchSemanticsNodes().isEmpty()
		}
	}

	@After
	fun tearDown() {
		EditorTestHarness.teardown(scenario, projectDef)
	}
}
