import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.fakeProjectsList
import com.darkrockstudios.apps.hammer.common.preview.projectselection.previewProject
import com.darkrockstudios.apps.hammer.common.projectselection.CreateProjectButtonTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectListTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectListUi
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

private const val PANE_TAG = "test-pane"
private const val PROJECT_COUNT = 20

/** The phone layout: [ProjectListUi] only shows the create bar at Compact width. */
class ProjectListFooterTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private fun showProjectList() {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.testTag(PANE_TAG).size(360.dp, 640.dp)) {
					ProjectListUi(
						component = fakeProjectsList(List(PROJECT_COUNT) { testProject(it) }),
						rootSnackbar = rememberRootSnackbarHostState(),
					)
				}
			}
		}
	}

	private fun SemanticsNodeInteraction.bounds() = fetchSemanticsNode().boundsInRoot

	@Test
	fun `Project list fills its pane beneath the create bar`() {
		showProjectList()

		val list = compose.onNodeWithTag(ProjectListTestTag).bounds()
		val createBar = compose.onNodeWithTag(CreateProjectButtonTestTag).bounds()

		assertTrue(
			list.bottom >= createBar.bottom,
			"Create bar must overlay the list, not shrink it: list bottom ${list.bottom}, bar bottom ${createBar.bottom}",
		)
	}

	@Test
	fun `Last project clears the create bar when scrolled to the bottom`() {
		showProjectList()

		compose.onNodeWithTag(ProjectListTestTag).performScrollToIndex(PROJECT_COUNT - 1)

		val createBar = compose.onNodeWithTag(CreateProjectButtonTestTag).bounds()
		val lastProject = compose.onNodeWithText(projectName(PROJECT_COUNT - 1)).bounds()

		assertTrue(
			lastProject.bottom <= createBar.top + 1f,
			"Last project must scroll clear of the create bar: row bottom ${lastProject.bottom}, bar top ${createBar.top}",
		)
	}

	@Test
	fun `Create bar stays visible at the bottom of the list`() {
		showProjectList()

		compose.onNodeWithTag(ProjectListTestTag).performScrollToIndex(PROJECT_COUNT - 1)

		compose.onNodeWithTag(CreateProjectButtonTestTag).assertIsDisplayed()
	}
}

private fun projectName(index: Int) = "Project ${index + 1}"

private fun testProject(index: Int) = previewProject(projectName(index), tags = emptySet())
