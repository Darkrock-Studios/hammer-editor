import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkrockstudios.apps.hammer.android.ProjectRootActivity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projectroot.NAV_ENCYCLOPEDIA_TAG
import com.darkrockstudios.apps.hammer.common.projectroot.NAV_EDITOR_TAG
import com.darkrockstudios.apps.hammer.common.projectroot.NAV_HOME_TAG
import com.darkrockstudios.apps.hammer.common.projectroot.NAV_NOTES_TAG
import com.darkrockstudios.apps.hammer.common.projectroot.NAV_TIMELINE_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke-tests every top-level editor destination by seeding a project, launching straight into
 * the editor, and clicking through each nav item. Composing a screen that throws fails the test.
 */
@RunWith(AndroidJUnit4::class)
class EditorNavigationTest {

	@get:Rule
	val composeRule = createEmptyComposeRule()

	private lateinit var projectDef: ProjectDef
	private lateinit var scenario: ActivityScenario<ProjectRootActivity>

	@Before
	fun setup() {
		projectDef = EditorTestHarness.seedProject("E2E Nav")
		scenario = EditorTestHarness.launchEditor(projectDef)
	}

	@Test
	fun visitsEveryTopLevelDestination() {
		// Editor opens on Home; the bottom bar must show all five destinations.
		composeRule.waitUntil(timeoutMillis = 10_000) {
			composeRule.onAllNodesWithTag(NAV_HOME_TAG).fetchSemanticsNodes().isNotEmpty()
		}

		listOf(NAV_EDITOR_TAG, NAV_NOTES_TAG, NAV_ENCYCLOPEDIA_TAG, NAV_TIMELINE_TAG, NAV_HOME_TAG)
			.forEach { tag ->
				composeRule.navigateTo(tag)
				composeRule.onNodeWithTag(tag).assertIsDisplayed()
			}
	}

	@After
	fun tearDown() {
		EditorTestHarness.teardown(scenario, projectDef)
	}
}
