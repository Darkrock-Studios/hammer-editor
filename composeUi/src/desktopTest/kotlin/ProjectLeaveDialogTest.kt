import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectData
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectLeaveDialog
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

class ProjectLeaveDialogTest {
	@get:Rule
	val compose = createComposeRule()

	private val projectDef = fakeProjectData().definition

	@Test
	fun `Leaving names the owner, keeps the copy and calls leave`() {
		val component = mockk<ProjectsList>(relaxed = true)
		compose.setContent { ProjectLeaveDialog(component, projectDef, ownerName = "Alice", close = {}) }

		compose.onNodeWithText("Alice's copy is not touched", substring = true).assertIsDisplayed()
		compose.onNodeWithText("Your copy stays on this device", substring = true).assertIsDisplayed()
		compose.onNodeWithText("Leave").performClick()
		compose.waitForIdle()

		verify { component.leaveProject(projectDef) }
		verify(exactly = 0) { component.deleteProject(any(), any()) }
	}

	@Test
	fun `Cancelling leaves nothing`() {
		val component = mockk<ProjectsList>(relaxed = true)
		compose.setContent { ProjectLeaveDialog(component, projectDef, ownerName = null, close = {}) }

		compose.onNodeWithText("The owner's copy is not touched", substring = true).assertIsDisplayed()
		compose.onNodeWithText("Cancel").performClick()
		compose.waitForIdle()

		verify(exactly = 0) { component.leaveProject(any()) }
	}
}
