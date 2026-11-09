import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.darkrockstudios.apps.hammer.common.components.projecthome.ProjectSettings
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectOwner
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectSync
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectSyncSharedTestTag
import com.darkrockstudios.apps.hammer.common.projecthome.ProjectSyncToggleTestTag
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class ProjectSyncSectionTest {
	@get:Rule
	val compose = createComposeRule()

	private fun render(state: ProjectSettings.SyncState) {
		compose.setContent {
			ProjectSync(section = 8, state = state, component = mockk(relaxed = true))
		}
	}

	@Test
	fun `An own project shows the sync switch`() {
		render(ProjectSettings.SyncState(serverConfigured = true))

		compose.onNodeWithTag(ProjectSyncToggleTestTag).assertExists()
		compose.onNodeWithTag(ProjectSyncSharedTestTag).assertDoesNotExist()
	}

	@Test
	fun `A shared project shows no sync switch`() {
		render(ProjectSettings.SyncState(serverConfigured = true, owner = ProjectOwner(7L, "Alice")))

		compose.onNodeWithTag(ProjectSyncToggleTestTag).assertDoesNotExist()
		compose.onNodeWithTag(ProjectSyncSharedTestTag).assertExists()
	}
}
