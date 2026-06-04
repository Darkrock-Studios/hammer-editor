import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkrockstudios.apps.hammer.android.ProjectRootActivity
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projecthome.PROJECT_STATS_TAG
import com.darkrockstudios.apps.hammer.common.projectroot.NAV_HOME_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Project-home happy path: the editor opens on the Home destination, which must render the project
 * stats dashboard. Re-navigating Home keeps it stable.
 */
@RunWith(AndroidJUnit4::class)
class ProjectHomeTest {

	@get:Rule
	val composeRule = createEmptyComposeRule()

	private lateinit var projectDef: ProjectDef
	private lateinit var scenario: ActivityScenario<ProjectRootActivity>

	@Before
	fun setup() {
		projectDef = EditorTestHarness.seedProject("E2E Home")
		scenario = EditorTestHarness.launchEditor(projectDef)
	}

	@Test
	fun homeShowsProjectStats() {
		composeRule.waitUntil(10_000) {
			composeRule.onAllNodesWithTag(PROJECT_STATS_TAG).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag(PROJECT_STATS_TAG).assertIsDisplayed()

		// Re-selecting Home keeps the stats dashboard stable.
		composeRule.navigateTo(NAV_HOME_TAG)
		composeRule.onNodeWithTag(PROJECT_STATS_TAG).assertIsDisplayed()
	}

	@After
	fun tearDown() {
		EditorTestHarness.teardown(scenario, projectDef)
	}
}
