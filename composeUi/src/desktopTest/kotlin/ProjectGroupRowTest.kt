import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.base.http.MAX_GROUP_NESTING_DEPTH
import com.darkrockstudios.apps.hammer.common.components.projectselection.GroupData
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.GroupDef
import com.darkrockstudios.apps.hammer.common.preview.projectselection.previewGroup
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectGroupActions
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectGroupRow
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectListRow
import com.darkrockstudios.apps.hammer.common.projectselection.projectGroupRowTag
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ProjectGroupRowTest {
	@get:Rule
	val compose = createComposeRule()

	private val group = previewGroup("The Salt Trilogy", description = "Three books about the drowned coast")

	private fun show(
		collapsed: Boolean = false,
		depth: Int = 1,
		projectCount: Int = 3,
		actions: ProjectGroupActions = ProjectGroupActions(),
	) {
		compose.setContent {
			ProjectGroupRow(
				row = ProjectListRow.Group(GroupData(group, collapsed), depth, projectCount),
				actions = actions,
			)
		}
	}

	@Test
	fun `shows the name, description and transitive project count`() {
		show()

		compose.onNodeWithText("The Salt Trilogy").assertIsDisplayed()
		compose.onNodeWithText("Three books about the drowned coast").assertIsDisplayed()
		compose.onNodeWithText("3 PROJECTS").assertIsDisplayed()
	}

	@Test
	fun `tapping the row toggles the group`() {
		val toggled = mutableListOf<GroupDef>()
		show(actions = ProjectGroupActions(onToggle = { toggled += it }))

		compose.onNodeWithTag(projectGroupRowTag(group.id)).performClick()

		assertEquals(listOf(group), toggled)
	}

	@Test
	fun `the chevron says whether the group is collapsed`() {
		show(collapsed = true)
		compose.onNodeWithContentDescription("Group collapsed", useUnmergedTree = true).assertExists()
	}

	@Test
	fun `the overflow menu creates a project in the group`() {
		val created = mutableListOf<GroupDef>()
		show(actions = ProjectGroupActions(onNewProject = { created += it }))

		compose.onNodeWithTag("More").performClick()
		compose.onNodeWithTag("New project here").performClick()

		assertEquals(listOf(group), created)
	}

	@Test
	fun `a group at the nesting limit offers no subgroup`() {
		show(depth = MAX_GROUP_NESTING_DEPTH)

		compose.onNodeWithTag("More").performClick()

		compose.onNodeWithTag("Rename").assertExists()
		compose.onNodeWithTag("New subgroup").assertDoesNotExist()
	}

	@Test
	fun `a group below the nesting limit offers a subgroup`() {
		val parents = mutableListOf<GroupDef>()
		show(depth = MAX_GROUP_NESTING_DEPTH - 1, actions = ProjectGroupActions(onNewSubgroup = { parents += it }))

		compose.onNodeWithTag("More").performClick()
		compose.onNodeWithTag("New subgroup").performClick()

		assertEquals(listOf(group), parents)
	}
}
