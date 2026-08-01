import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.darkrockstudios.apps.hammer.android.ProjectRootActivity
import com.darkrockstudios.apps.hammer.android.ProjectSelectActivity
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.projectselection.CreateProjectButtonTestTag
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.getKoin

/**
 * End-to-end test driving the real running app on a device/emulator:
 * launch the project-select screen, create a project, see it in the list, and open it.
 *
 * Unlike the desktop UI tests (which drive isolated composables with fake components),
 * this launches the actual [ProjectSelectActivity] with the real Koin graph and writes a
 * real project to the device filesystem — so it uses a unique name and cleans up after.
 */
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@RunWith(AndroidJUnit4::class)
class ProjectLifecycleTest {

	@get:Rule
	val composeRule = createAndroidComposeRule<ProjectSelectActivity>()

	// Unique per run so re-runs don't collide on the "already exists" validation.
	private val projectName = "E2E Smoke ${System.currentTimeMillis()}"

	@Test
	fun createProjectThenOpenIt() {
		// 1. Open the create dialog (masthead button when wide, bottom bar when narrow).
		// 2. Type the project name into the dialog's only editable field, then confirm.
		composeRule.clickUntil(hasTestTag(CreateProjectButtonTestTag)) {
			composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNode(hasSetTextAction()).performTextInput(projectName)

		// The confirm button and the dialog title both read "Create Project";
		// only the button is clickable, so combine the matchers to disambiguate.
		composeRule.onNode(hasText("Create Project").and(hasClickAction())).performClick()

		// 3. createProject and the list refresh run off the test thread, so poll rather than
		// asserting immediately. First wait for the dialog to dismiss - until its editable field
		// is gone it also matches the project name - then for the new project's card to appear.
		// It sorts to the top (lastAccessed = now), so no scrolling is needed.
		composeRule.waitUntil(timeoutMillis = 10_000) {
			composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()
		}
		composeRule.waitUntil(timeoutMillis = 10_000) {
			composeRule.onAllNodesWithText(projectName).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithText(projectName).assertIsDisplayed()

		// 4. Open it: tapping the card launches ProjectRootActivity. Verify the navigation
		// fired via an ActivityMonitor rather than coupling to the second activity's tree.
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		val monitor = instrumentation.addMonitor(ProjectRootActivity::class.java.name, null, false)

		composeRule.onNodeWithText(projectName).performClick()

		val opened = monitor.waitForActivityWithTimeout(10_000)
		assertNotNull("Opening a project should launch ProjectRootActivity", opened)
		instrumentation.removeMonitor(monitor)

		// Finish the project activity and wait for its destroy - and the Koin project-scope close
		// that flushes scene buffers - to complete before @After deletes the project directory.
		// Otherwise teardown races the async scope close and crashes the process.
		opened?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
		instrumentation.waitForIdleSync()
	}

	@After
	fun deleteCreatedProject() {
		val repository = getKoin().get<ProjectsRepository>()
		EditorTestHarness.deleteProjectWhenSettled(repository.getProjectDefinition(projectName))
	}
}
