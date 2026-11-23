import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
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
import com.darkrockstudios.apps.hammer.common.preview.ScreenSceneListUiTabletPreview
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_MENU_TAG
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_TITLE_TAG
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PANE_TAG = "scene-list-header-pane"

/** The scene list header keeps its title and menu whole and apart in a narrow pane. */
class SceneListHeaderTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private var paneWidth by mutableStateOf(400.dp)

	private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

	private fun assertHeaderFits(width: Dp) {
		compose.setContent {
			Box(Modifier.testTag(PANE_TAG).width(paneWidth).height(600.dp)) { ScreenSceneListUiTabletPreview() }
		}
		val fullMenu = bounds(SCENE_LIST_MENU_TAG).width

		paneWidth = width
		compose.waitForIdle()
		val pane = bounds(PANE_TAG)
		val title = bounds(SCENE_LIST_TITLE_TAG)
		val menu = bounds(SCENE_LIST_MENU_TAG)

		assertEquals(fullMenu, menu.width, 0.5f)
		assertTrue(menu.right <= pane.right + 0.5f, "The menu runs past the pane at $width")
		assertTrue(title.right <= menu.left + 0.5f, "The menu overlaps the title at $width")
	}

	@Test
	fun `Header fits a narrow pane`() = assertHeaderFits(200.dp)
}
