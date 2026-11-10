import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectData
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectData
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCardCoverTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCardSharedByTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCardSyncOffTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectCardTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectIndexRow
import io.mockk.*
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

private const val SyncItem = "Sync this project"

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
		data = data.copy(coverImagePath = "/projects/Test/images/4~Cover.png", coverImageVersion = "stamp:10-20")
		compose.onNodeWithTag(ProjectCardCoverTestTag, useUnmergedTree = true).assertExists()
	}

	@Test
	fun `Project Card with a cover keeps the height of one without`() {
		val plain = fakeProjectData()
		val covered = plain.copy(coverImagePath = "/projects/Test/images/4~Cover.png", coverImageVersion = "stamp:10-20")

		// A scrolling column gives the rows unbounded height, as the project list does.
		compose.setContent {
			Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
				for (isWide in listOf(true, false)) {
					for (data in listOf(plain, covered)) {
						Box(modifier = Modifier.testTag(rowTag(isWide, data.coverImagePath != null))) {
							ProjectIndexRow(
								isWide = isWide,
								index = 0,
								projectData = data,
								onProjectClick = {},
								onProjectAltClick = {},
								onProjectRenameClick = {},
							)
						}
					}
				}
			}
		}

		for (isWide in listOf(true, false)) {
			val plainHeight = compose.onNodeWithTag(rowTag(isWide, hasCover = false)).getUnclippedBoundsInRoot().height
			val coveredHeight = compose.onNodeWithTag(rowTag(isWide, hasCover = true)).getUnclippedBoundsInRoot().height
			assertEquals(plainHeight, coveredHeight, "isWide=$isWide")
		}
	}

	private fun rowTag(isWide: Boolean, hasCover: Boolean) = "row-wide-$isWide-cover-$hasCover"

	@Test
	fun `Project Card shows the sync-off marker for a project with sync off and a server`() {
		val data = fakeProjectData()
		var current by mutableStateOf(data)
		var isServerSynced by mutableStateOf(true)
		compose.setContent {
			ProjectIndexRow(
				isWide = true,
				index = 0,
				projectData = current,
				onProjectClick = {},
				onProjectAltClick = {},
				onProjectRenameClick = {},
				isServerSynced = isServerSynced,
			)
		}

		compose.onNodeWithTag(ProjectCardSyncOffTestTag, useUnmergedTree = true).assertDoesNotExist()
		current = data.copy(metadata = data.metadata.copy(info = data.metadata.info.copy(syncEnabled = false)))
		compose.onNodeWithTag(ProjectCardSyncOffTestTag, useUnmergedTree = true).assertIsDisplayed()
		isServerSynced = false
		compose.onNodeWithTag(ProjectCardSyncOffTestTag, useUnmergedTree = true).assertDoesNotExist()
	}

	@Test
	fun `Project Card switches sync on this device for an own project`() {
		val data = fakeProjectData()
		var current by mutableStateOf(data)
		val clicks = mutableListOf<Pair<ProjectDef, Boolean>>()
		compose.setContent {
			ProjectIndexRow(
				isWide = true,
				index = 0,
				projectData = current,
				onProjectClick = {},
				onProjectAltClick = {},
				onProjectRenameClick = {},
				onProjectSyncClick = { def, enabled -> clicks += def to enabled },
			)
		}

		compose.onNodeWithTag("More").performClick()
		compose.onNodeWithTag(SyncItem).assertIsOn().performClick()
		current = data.copy(metadata = data.metadata.copy(info = data.metadata.info.copy(syncEnabled = false)))
		compose.onNodeWithTag("More").performClick()
		compose.onNodeWithTag(SyncItem).assertIsOff().performClick()

		assertEquals(listOf(data.definition to false, data.definition to true), clicks)
	}

	@Test
	fun `Project Card offers no sync switch for a shared project or without a server`() {
		var data by mutableStateOf(fakeProjectData().shared(ownerName = "Alice"))
		var isServerSynced by mutableStateOf(true)
		compose.setContent {
			ProjectIndexRow(
				isWide = true,
				index = 0,
				projectData = data,
				onProjectClick = {},
				onProjectAltClick = {},
				onProjectRenameClick = {},
				isServerSynced = isServerSynced,
			)
		}

		compose.onNodeWithTag("More").performClick()
		compose.onNodeWithTag(SyncItem).assertDoesNotExist()
		data = fakeProjectData()
		isServerSynced = false
		compose.onNodeWithTag("Delete").assertExists()
		compose.onNodeWithTag(SyncItem).assertDoesNotExist()
	}

	@Test
	fun `Project Card names the owner of a shared project`() {
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

		compose.onNodeWithTag(ProjectCardSharedByTestTag, useUnmergedTree = true).assertDoesNotExist()
		data = data.shared(ownerName = "Alice")
		compose.onNodeWithText("Shared by Alice", useUnmergedTree = true).assertIsDisplayed()
		data = data.shared(ownerName = null)
		compose.onNodeWithText("Shared with you", useUnmergedTree = true).assertIsDisplayed()
	}

	@Test
	fun `Project Card offers no rename or move for a shared project`() {
		compose.setContent {
			ProjectIndexRow(
				isWide = true,
				index = 0,
				projectData = fakeProjectData().shared(ownerName = "Alice"),
				onProjectClick = {},
				onProjectAltClick = {},
				onProjectRenameClick = {},
			)
		}

		compose.onNodeWithTag("More").performClick()
		compose.onNodeWithTag("Leave").assertExists()
		compose.onNodeWithTag("Delete").assertDoesNotExist()
		compose.onNodeWithTag("Rename").assertDoesNotExist()
		compose.onNodeWithTag("Move to…").assertDoesNotExist()
	}

	@Test
	fun `Project Card renames and moves a parked shared project`() {
		val data = fakeProjectData().shared(ownerName = "Alice")
		val parked = data.copy(metadata = data.metadata.copy(info = data.metadata.info.copy(syncEnabled = false)))
		val renamed = mutableListOf<ProjectDef>()
		compose.setContent {
			ProjectIndexRow(
				isWide = true,
				index = 0,
				projectData = parked,
				onProjectClick = {},
				onProjectAltClick = {},
				onProjectRenameClick = { renamed += it },
			)
		}

		compose.onNodeWithTag("More").performClick()
		compose.onNodeWithTag("Move to…").assertExists()
		compose.onNodeWithTag("Rename").performClick()

		assertEquals(listOf(parked.definition), renamed)
	}

	@Test
	fun `Project Card renames and moves a shared project with no server`() {
		compose.setContent {
			ProjectIndexRow(
				isWide = true,
				index = 0,
				projectData = fakeProjectData().shared(ownerName = "Alice"),
				onProjectClick = {},
				onProjectAltClick = {},
				onProjectRenameClick = {},
				isServerSynced = false,
			)
		}

		compose.onNodeWithTag("More").performClick()
		compose.onNodeWithTag("Leave").assertExists()
		compose.onNodeWithTag("Rename").assertExists()
		compose.onNodeWithTag("Move to…").assertExists()
	}

	@Test
	fun `Project Card deletes a parked shared project`() {
		val data = fakeProjectData().shared(ownerName = "Alice")
		val parked = data.copy(metadata = data.metadata.copy(info = data.metadata.info.copy(syncEnabled = false)))
		val deleted = mutableListOf<ProjectDef>()
		compose.setContent {
			ProjectIndexRow(
				isWide = true,
				index = 0,
				projectData = parked,
				onProjectClick = {},
				onProjectAltClick = { deleted += it },
				onProjectRenameClick = {},
			)
		}

		compose.onNodeWithTag("More").performClick()
		compose.onNodeWithTag("Leave").assertDoesNotExist()
		compose.onNodeWithTag("Delete").performClick()

		assertEquals(listOf(parked.definition), deleted)
	}

	private fun ProjectData.shared(ownerName: String?) =
		copy(metadata = metadata.copy(info = metadata.info.copy(ownerId = 7L, ownerName = ownerName)))
}
