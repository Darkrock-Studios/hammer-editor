import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projectselection.about.AppUpdateStatus
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AppUpdateStatusTest {
	@get:Rule
	val compose = createComposeRule()

	private var checks = 0
	private var updates = 0

	private fun show(state: AppUpdateState) {
		compose.setContent {
			AppTheme(globalSettingsPreview) {
				AppUpdateStatus(
					state = state,
					onCheckForUpdate = { checks++ },
					onUpdateApp = { updates++ },
				)
			}
		}
	}

	@Test
	fun `a build that cannot update shows nothing`() {
		show(AppUpdateState.Unsupported)
		compose.onNodeWithText("Check for Updates").assertDoesNotExist()
	}

	@Test
	fun `check for updates is offered before and after a check`() {
		show(AppUpdateState.Idle)
		compose.onNodeWithText("Check for Updates").performClick()
		assertEquals(1, checks)

		show(AppUpdateState.UpToDate)
		compose.onNodeWithText("Hammer is up to date.").assertIsDisplayed()
		compose.onNodeWithText("Check for Updates").assertIsDisplayed()
	}

	@Test
	fun `the button is disabled while checking`() {
		show(AppUpdateState.Checking)
		compose.onNodeWithText("Check for Updates").assertIsNotEnabled()
	}

	@Test
	fun `an installable release offers the update`() {
		show(AppUpdateState.Available(version = "9.9.9", installable = true))

		compose.onNodeWithText("Hammer 9.9.9 is available.").assertIsDisplayed()
		compose.onNodeWithText("Update to 9.9.9").performClick()
		assertEquals(1, updates)
	}

	@Test
	fun `a release this install cannot apply is only announced`() {
		show(AppUpdateState.Available(version = "9.9.9", installable = false))

		compose.onNodeWithText("Hammer 9.9.9 is available.").assertIsDisplayed()
		compose.onNodeWithText("Update to 9.9.9").assertDoesNotExist()
	}

	@Test
	fun `a failure offers a retry`() {
		show(AppUpdateState.Failed(version = "9.9.9", reason = "HTTP 503"))

		compose.onNodeWithText("Could not update to 9.9.9: HTTP 503").assertIsDisplayed()
		compose.onNodeWithText("Retry").performClick()
		assertEquals(1, updates)
	}
}
