import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCard
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCardTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.fakeProjectData
import io.mockk.*
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
				onProjectAltClick = onProjectAltClick
			)
		}

		compose.onNodeWithContentDescription("Delete").performClick()
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
				onProjectAltClick = {}
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
				onProjectAltClick = {}
			)
		}

		compose.onNodeWithText(data.definition.name).assertIsDisplayed()
	}
}