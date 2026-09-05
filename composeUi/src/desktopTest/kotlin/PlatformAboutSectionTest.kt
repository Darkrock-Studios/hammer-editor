import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projectselection.aboutapp.AboutApp
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projectselection.about.PlatformAboutSection
import org.junit.Rule
import org.junit.Test

/**
 * The support case behind the export button was a user who could not get at their logs, so both
 * routes to them have to actually render.
 */
class PlatformAboutSectionTest {
	@get:Rule
	val compose = createComposeRule()

	private class FakeAboutApp(logDirectoryPath: String) : AboutApp {
		override val state: Value<AboutApp.State> =
			MutableValue(AboutApp.State(logDirectoryPath = logDirectoryPath))

		override fun openDiscord() = Unit
		override fun openReddit() = Unit
		override fun openGithub() = Unit
		override fun viewChangelog() = Unit
		override fun openLatestRelease() = Unit
	}

	@Test
	fun `both log actions and the log directory are shown`() {
		val logDir = "/home/someone/.config/DarkrockStudios/hammer/0/logs"
		compose.setContent {
			AppTheme(globalSettingsPreview) {
				PlatformAboutSection(FakeAboutApp(logDir), section = 0)
			}
		}

		compose.onNodeWithText("Application Logs").assertIsDisplayed()
		compose.onNodeWithText(logDir).assertIsDisplayed()
		compose.onNodeWithText("Open log directory").assertIsDisplayed()
		compose.onNodeWithText("Export Logs").assertIsDisplayed()
	}
}
