import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.projectselection.CreateGroupButtonTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.CreateProjectButtonTestTag
import com.darkrockstudios.apps.hammer.common.projectselection.Masthead
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectsSortMode
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MASTHEAD_TAG = "test-masthead"

/** The desktop window that was reported clipping (about 808dp), beside the collapsed nav rail. */
private val ReportedPaneWidth: Dp = 808.dp - 72.dp

/** The narrowest wide layout: a 600dp window beside the collapsed nav rail. */
private val NarrowestPaneWidth: Dp = 600.dp - 72.dp

private val ReferencePaneWidth: Dp = 1600.dp

/** The wide masthead, which carries the create buttons. */
class ProjectListMastheadTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private var paneWidth by mutableStateOf(ReferencePaneWidth)

	private fun showMasthead() {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.testTag(MASTHEAD_TAG).width(paneWidth)) {
					Masthead(
						entryCount = 128,
						showEntryCount = true,
						isServerSynced = true,
						onSync = {},
						onCreate = {},
						onCreateGroup = {},
						showCreate = true,
						horizontalPadding = Ui.Padding.XXL,
						sortMode = ProjectsSortMode.LastAccessedDesc,
						onSortChange = {},
						showSort = true,
						searchActive = false,
						onToggleSearch = {},
					)
				}
			}
		}
	}

	private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

	private fun assertButtonsUnclipped(width: Dp) {
		paneWidth = ReferencePaneWidth
		compose.waitForIdle()
		val fullCreate = bounds(CreateProjectButtonTestTag).width
		val fullGroup = bounds(CreateGroupButtonTestTag).width

		paneWidth = width
		compose.waitForIdle()
		val pane = bounds(MASTHEAD_TAG)
		val create = bounds(CreateProjectButtonTestTag)
		val group = bounds(CreateGroupButtonTestTag)

		assertEquals(fullCreate, create.width, 0.5f, "Create Project is squeezed at $width")
		assertEquals(fullGroup, group.width, 0.5f, "New Group is squeezed at $width")
		assertTrue(create.right <= pane.right + 0.5f, "Create Project runs past the pane at $width")
		assertTrue(group.right <= create.left, "New Group overlaps Create Project at $width")
	}

	@Test
	fun `Create buttons keep their full width at the reported window size`() {
		showMasthead()
		assertButtonsUnclipped(ReportedPaneWidth)
	}

	@Test
	fun `Create buttons keep their full width in the narrowest wide layout`() {
		showMasthead()
		assertButtonsUnclipped(NarrowestPaneWidth)
	}
}
