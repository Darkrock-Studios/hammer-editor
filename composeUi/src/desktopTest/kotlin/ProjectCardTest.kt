import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectData
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCard
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCardTestTag
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test

class ProjectCardTest {
	@get:Rule
	val compose = createComposeRule()

	@Test
	fun `Project Card Delete`() {
		val data = fakeProjectData()

		val onProjectAltClick = mockk<(projectDef: ProjectDef) -> Unit>()
		every { onProjectAltClick(any()) } just Runs

		compose.setContent {
			ProjectCard(
				projectData = data,
				onProjectClick = {},
				onProjectAltClick = onProjectAltClick,
				onProjectRenameClick = {},
			)
		}

		compose.onNodeWithTag("More").performClick()
		compose.onNodeWithTag("Delete").performClick()

		verify(exactly = 1) { onProjectAltClick(data.definition) }
	}

	@Test
	fun `Project Card Tap`() {
		val data = fakeProjectData()

		val onProjectClick = mockk<(projectDef: ProjectDef) -> Unit>()
		every { onProjectClick(any()) } just Runs

		compose.setContent {
			ProjectCard(
				projectData = data,
				onProjectClick = onProjectClick,
				onProjectAltClick = {},
				onProjectRenameClick = {},
			)
		}

		compose.onNodeWithTag(ProjectCardTestTag).performClick()
		verify(exactly = 1) { onProjectClick(data.definition) }
	}

	@Test
	fun `Project Card Contents`() {
		val data = fakeProjectData()

		compose.setContent {
			ProjectCard(
				projectData = data,
				onProjectClick = {},
				onProjectAltClick = {},
				onProjectRenameClick = {},
			)
		}

		compose.onNodeWithText(data.definition.name).assertIsDisplayed()
	}
}