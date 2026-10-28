import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectData
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCardCoverTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCardTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectIndexRow
import io.mockk.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ProjectCardTest {
	@get:Rule
	val compose = createComposeRule()

	@Test
	fun `Project Card Delete`() {
		val data = fakeProjectData()

		val onProjectAltClick = mockk<(projectDef: ProjectDef) -> Unit>()
		every { onProjectAltClick(any()) } just Runs

		compose.setContent {
			ProjectIndexRow(
				isWide = true,
				index = 0,
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
			ProjectIndexRow(
				isWide = true,
				index = 0,
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
			ProjectIndexRow(
				isWide = true,
				index = 0,
				projectData = data,
				onProjectClick = {},
				onProjectAltClick = {},
				onProjectRenameClick = {},
			)
		}

		compose.onNodeWithText(data.definition.name).assertIsDisplayed()
	}

	@Test
	fun `Project Card Move`() {
		val data = fakeProjectData()
		val moved = mutableListOf<ProjectDef>()

		compose.setContent {
			ProjectIndexRow(
				isWide = true,
				index = 0,
				projectData = data,
				onProjectClick = {},
				onProjectAltClick = {},
				onProjectRenameClick = {},
				onProjectMoveClick = { moved += it },
			)
		}

		compose.onNodeWithTag("More").performClick()
		compose.onNodeWithTag("Move to…").performClick()

		assertEquals(listOf(data.definition), moved)
	}

	@Test
	fun `Project Card shows a cover only when it has one`() {
		var data by mutableStateOf(fakeProjectData())

		compose.setContent {
			ProjectIndexRow(
				isWide = true,
				index = 0,
				projectData = data,
				onProjectClick = {},
				onProjectAltClick = {},
				onProjectRenameClick = {},
			)
		}

		compose.onNodeWithTag(ProjectCardCoverTestTag, useUnmergedTree = true).assertDoesNotExist()
		data = data.copy(coverImagePath = "/projects/Test/images/4~Cover.png", coverImageVersion = "10-20")
		compose.onNodeWithTag(ProjectCardCoverTestTag, useUnmergedTree = true).assertExists()
	}
}
