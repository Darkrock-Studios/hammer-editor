import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projectselection.about.ReportBugSection
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ReportBugSectionTest {
	@get:Rule
	val compose = createComposeRule()

	@Test
	fun `the report button sends the user on`() {
		var reports = 0
		compose.setContent {
			AppTheme(globalSettingsPreview) {
				ReportBugSection(section = 0, onReportBug = { reports++ })
			}
		}

		compose.onNodeWithText("Report a Bug").assertIsDisplayed()
		compose.onNodeWithText("Report on GitHub").performClick()

		assertEquals(1, reports)
	}
}
