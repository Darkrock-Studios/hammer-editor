import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.help.HELP_BUTTON_TAG
import com.darkrockstudios.apps.hammer.common.help.HelpScreen
import com.darkrockstudios.apps.hammer.common.help.helpTopicTag
import com.darkrockstudios.apps.hammer.common.preview.ScreenSceneListUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.encyclopedia.ScreenBrowseEntriesUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.notes.ScreenBrowseNotesUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.projecthome.ScreenProjectStatsUiTabletPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.ScreenTimeLineOverviewUiTabletPreview
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SCENE_LIST_MENU_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.PROJECT_HOME_MENU_TAG
import org.junit.Rule
import org.junit.Test

/** Each of the five in-project destinations offers help (a "?", or on Home and Scenes a menu item) that opens its own topics. */
class HelpButtonPlacementTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private fun assertHelpOpens(help: HelpScreen, menuTag: String? = null, content: @Composable () -> Unit) {
		compose.setContent {
			Box(Modifier.size(1280.dp, 800.dp)) { content() }
		}

		menuTag?.let { compose.onNodeWithTag(it).performClick() }
		compose.onAllNodesWithTag(HELP_BUTTON_TAG).assertCountEquals(1)
		compose.onNodeWithTag(HELP_BUTTON_TAG).performClick()
		help.topics.forEach { compose.onNodeWithTag(helpTopicTag(it)).assertExists() }
	}

	@Test
	fun `Project home has help in its menu`() = assertHelpOpens(HelpScreen.ProjectHome, menuTag = PROJECT_HOME_MENU_TAG) { ScreenProjectStatsUiTabletPreview() }

	@Test
	fun `Scenes has help in its menu`() = assertHelpOpens(HelpScreen.Scenes, menuTag = SCENE_LIST_MENU_TAG) { ScreenSceneListUiTabletPreview() }

	@Test
	fun `Encyclopedia has help`() = assertHelpOpens(HelpScreen.Encyclopedia) { ScreenBrowseEntriesUiTabletPreview() }

	@Test
	fun `Notes has help`() = assertHelpOpens(HelpScreen.Notes) { ScreenBrowseNotesUiTabletPreview() }

	@Test
	fun `Timeline has help`() = assertHelpOpens(HelpScreen.Timeline) { ScreenTimeLineOverviewUiTabletPreview() }
}
