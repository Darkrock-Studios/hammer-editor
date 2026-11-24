import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.help.HELP_WATCH_ALL_TAG
import com.darkrockstudios.apps.hammer.common.help.HelpSection
import com.darkrockstudios.apps.hammer.common.projectselection.about.ABOUT_HELP_BUTTON_TAG
import com.darkrockstudios.apps.hammer.common.projectselection.about.ScreenAboutAppUiTabletPreview
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test

class AboutHelpTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	@Test
	fun `About's help button opens every help section`() {
		compose.setContent {
			Box(Modifier.size(1280.dp, 800.dp)) { ScreenAboutAppUiTabletPreview() }
		}

		compose.onNodeWithTag(ABOUT_HELP_BUTTON_TAG).performClick()

		compose.onNodeWithTag(HELP_WATCH_ALL_TAG).assertExists()
		HelpSection.entries.forEach { section ->
			compose.onNodeWithText(runBlocking { getString(section.title) }).assertExists()
		}
	}
}
