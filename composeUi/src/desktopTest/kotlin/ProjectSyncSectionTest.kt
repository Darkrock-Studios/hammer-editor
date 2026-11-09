import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectOwner
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectSync
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectSyncOwnTestTag
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectSyncSharedTestTag
import org.junit.Rule
import org.junit.Test

class ProjectSyncSectionTest {
	@get:Rule
	val compose = createComposeRule()

	private fun render(state: ProjectSettings.SyncState) {
		compose.setContent {
			ProjectSync(section = 8, state = state)
		}
	}

	@Test
	fun `An own project states whether it syncs here`() {
		render(ProjectSettings.SyncState(serverConfigured = true, syncEnabled = false))

		compose.onNodeWithTag(ProjectSyncOwnTestTag).assertExists()
		compose.onNodeWithText("Kept on this device only").assertExists()
		compose.onNodeWithTag(ProjectSyncSharedTestTag).assertDoesNotExist()
	}

	@Test
	fun `A shared project names its owner`() {
		render(ProjectSettings.SyncState(serverConfigured = true, owner = ProjectOwner(7L, "Alice")))

		compose.onNodeWithTag(ProjectSyncOwnTestTag).assertDoesNotExist()
		compose.onNodeWithTag(ProjectSyncSharedTestTag).assertExists()
	}
}
