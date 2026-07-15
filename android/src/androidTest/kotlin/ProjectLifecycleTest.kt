import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.darkrockstudios.apps.hammer.android.ProjectRootActivity
import com.darkrockstudios.apps.hammer.android.ProjectSelectActivity
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.projectselection.CreateProjectButtonTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.CreateProjectNameFieldTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCardTestTag
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.getKoin

/**
 * End-to-end test driving the real running app on a device/emulator:
 * launch the project-select screen (the launcher activity), create a project, see it in the
 * list, and open it.
 *
 * Unlike the desktop UI tests (which drive isolated composables with fake components),
 * this launches the actual [ProjectSelectActivity] with the real Koin graph and writes a
 * real project to the device filesystem — so it uses a unique name and cleans up after.
 *
 * Targets the same stable Compose [testTag]s the iOS onboarding suite uses (see
 * `ios/iosUITests/HammerUITest.swift` `createAndOpenProject`) rather than matching on
 * display copy, so both suites stay in lockstep and localization can't break the test.
 */
@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@RunWith(AndroidJUnit4::class)
class ProjectLifecycleTest {

	@get:Rule
	val composeRule = createAndroidComposeRule<ProjectSelectActivity>()

	// Unique per run so re-runs don't collide on the "already exists" validation.
	private val projectName = "E2E Smoke ${System.currentTimeMillis()}"

	// The card exposes its project name via contentDescription ("Project <name>"); the tag is
	// shared across all cards, so combine them to pick out the one we just made.
	private val newProjectCard = hasTestTag(ProjectCardTestTag)
		.and(hasContentDescription(projectName, substring = true))

	@Test
	fun createProjectThenOpenIt() {
		// 1. Open the create dialog (masthead button when wide, bottom bar when narrow).
		composeRule.onNodeWithTag(CreateProjectButtonTestTag).performClick()

		// 2. Type the project name into the dialog's name field, then confirm.
		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag(CreateProjectNameFieldTestTag)).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag(CreateProjectNameFieldTestTag).performTextInput(projectName)

		// The confirm button has no dedicated tag; the button and the dialog title both read
		// "Create Project", so combine text with a click action to hit the button.
		composeRule.onNode(hasText("Create Project").and(hasClickAction())).performClick()

		// 3. createProject and the list refresh run off the test thread, so poll rather than
		// asserting immediately. First wait for the dialog to dismiss (its name field is gone),
		// then for the new project's card to appear. It sorts to the top (lastAccessed = now),
		// so no scrolling is needed.
		composeRule.waitUntil(timeoutMillis = 10_000) {
			composeRule.onAllNodes(hasTestTag(CreateProjectNameFieldTestTag)).fetchSemanticsNodes().isEmpty()
		}
		composeRule.waitUntil(timeoutMillis = 10_000) {
			composeRule.onAllNodes(newProjectCard).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNode(newProjectCard).assertIsDisplayed()

		// 4. Open it: tapping the card launches ProjectRootActivity. Verify the navigation
		// fired via an ActivityMonitor rather than coupling to the second activity's tree.
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		val monitor = instrumentation.addMonitor(ProjectRootActivity::class.java.name, null, false)

		composeRule.onNode(newProjectCard).performClick()

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
		repository.deleteProject(repository.getProjectDefinition(projectName))
	}
}
