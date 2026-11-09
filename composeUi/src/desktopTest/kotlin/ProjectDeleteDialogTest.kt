import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.darkrockstudios.apps.hammer.common.components.projectselection.projectslist.ProjectsList
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectData
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.darkrockstudios.apps.hammer.common.projectselection.DeleteCoauthorsNoteTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.DeleteOnServerToggleTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectDeleteDialog
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

class ProjectDeleteDialogTest {
	@get:Rule
	val compose = createComposeRule()

	private val projectDef = fakeProjectData().definition

	private fun confirmDelete() {
		compose.onNode(hasSetTextAction()).performTextInput(projectDef.name)
		compose.onNodeWithText("Delete").performClick()
		compose.waitForIdle()
	}

	@Test
	fun `No server option unless offered`() {
		val component = mockk<ProjectsList>(relaxed = true)
		compose.setContent { ProjectDeleteDialog(component, projectDef, close = {}) }

		compose.onNodeWithTag(DeleteOnServerToggleTestTag).assertDoesNotExist()
		confirmDelete()

		verify { component.deleteProject(projectDef, alsoDeleteOnServer = false) }
	}

	@Test
	fun `Co-authors are mentioned only when the delete can reach the server`() {
		var reach by mutableStateOf(Reach.Local)
		compose.setContent {
			ProjectDeleteDialog(
				mockk(relaxed = true),
				projectDef,
				close = {},
				offerServerDelete = reach == Reach.Offered,
				deletesOnServer = reach == Reach.Always,
			)
		}

		compose.onNodeWithTag(DeleteCoauthorsNoteTestTag).assertDoesNotExist()
		reach = Reach.Offered
		compose.onNodeWithTag(DeleteCoauthorsNoteTestTag).assertExists()
		reach = Reach.Always
		compose.onNodeWithTag(DeleteCoauthorsNoteTestTag).assertExists()
	}

	private enum class Reach { Local, Offered, Always }

	@Test
	fun `The offered server option is off by default and can be turned on`() {
		val keep = mockk<ProjectsList>(relaxed = true)
		compose.setContent { ProjectDeleteDialog(keep, projectDef, close = {}, offerServerDelete = true) }

		compose.onNodeWithTag(DeleteOnServerToggleTestTag).assertExists()
		confirmDelete()

		verify { keep.deleteProject(projectDef, alsoDeleteOnServer = false) }
	}

	@Test
	fun `Turning the server option on deletes the server copy too`() {
		val both = mockk<ProjectsList>(relaxed = true)
		compose.setContent { ProjectDeleteDialog(both, projectDef, close = {}, offerServerDelete = true) }

		compose.onNodeWithTag(DeleteOnServerToggleTestTag).performClick()
		confirmDelete()

		verify { both.deleteProject(projectDef, alsoDeleteOnServer = true) }
	}
}
