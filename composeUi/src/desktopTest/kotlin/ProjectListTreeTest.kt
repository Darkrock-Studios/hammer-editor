import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.projectselection.GroupData
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.projectselection.fakeProjectsList
import com.darkrockstudios.apps.hammer.common.preview.projectselection.previewGroup
import com.darkrockstudios.apps.hammer.common.preview.projectselection.previewProject
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectListUi
import com.darkrockstudios.apps.hammer.common.projectselection.projectGroupRowTag
import org.junit.Rule
import org.junit.Test

class ProjectListTreeTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private val trilogy = previewGroup("Trilogy")
	private val drafts = previewGroup("Drafts", parent = trilogy)
	private val workshop = previewGroup("Workshop")
	private val bookOne = previewProject("Book One", tags = emptySet(), parent = trilogy)
	private val undertow = previewProject("Undertow", tags = emptySet(), parent = drafts)
	private val exercise = previewProject("Exercise", tags = emptySet(), parent = workshop)
	private val loose = previewProject("Loose Leaf", tags = emptySet())

	private fun showList() {
		val component = fakeProjectsList(
			projects = listOf(bookOne, undertow, exercise, loose),
			groups = listOf(GroupData(trilogy), GroupData(workshop, collapsed = true), GroupData(drafts)),
			placement = mapOf(
				bookOne.definition.id to trilogy.id,
				undertow.definition.id to drafts.id,
				exercise.definition.id to workshop.id,
				loose.definition.id to null,
			),
		)
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.size(900.dp, 1000.dp)) {
					ProjectListUi(component = component, rootSnackbar = rememberRootSnackbarHostState())
				}
			}
		}
	}

	@Test
	fun `an expanded group shows its projects and subgroups`() {
		showList()

		compose.onNodeWithTag(projectGroupRowTag(trilogy.id)).assertIsDisplayed()
		compose.onNodeWithTag(projectGroupRowTag(drafts.id)).assertIsDisplayed()
		compose.onNodeWithText("Book One").assertIsDisplayed()
		compose.onNodeWithText("Undertow").assertIsDisplayed()
		compose.onNodeWithText("Loose Leaf").assertIsDisplayed()
	}

	@Test
	fun `a collapsed group hides its projects`() {
		showList()

		compose.onNodeWithTag(projectGroupRowTag(workshop.id)).assertIsDisplayed()
		compose.onNodeWithText("Exercise").assertDoesNotExist()
	}

	@Test
	fun `a filter flattens the list and names each match's groups`() {
		showList()

		compose.onNodeWithTag("projects-list-search-toggle").performClick()
		compose.onNodeWithTag("projects-list-search").performTextInput("e")
		compose.waitForIdle()

		compose.onNodeWithTag(projectGroupRowTag(trilogy.id)).assertDoesNotExist()
		compose.onNodeWithText("Exercise").assertIsDisplayed()
		compose.onNodeWithText("WORKSHOP").assertIsDisplayed()
		compose.onNodeWithText("Undertow").assertIsDisplayed()
		compose.onNodeWithText("TRILOGY › DRAFTS").assertIsDisplayed()
	}
}
