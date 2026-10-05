import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.appupdate.AppUpdateState
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projectselection.AppUpdateBanner
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class AppUpdateBannerTest {
	@get:Rule
	val compose = createComposeRule()

	private var updates = 0
	private var releasePages = 0
	private var dismissals = 0

	private fun show(state: AppUpdateState) {
		compose.setContent {
			AppTheme(globalSettingsPreview) {
				AppUpdateBanner(
					state = state,
					onUpdate = { updates++ },
					onOpenRelease = { releasePages++ },
					onDismiss = { dismissals++ },
				)
			}
		}
	}

	@Test
	fun `nothing is drawn until a release is available`() {
		show(AppUpdateState.Idle)
		compose.onNodeWithText("Later").assertDoesNotExist()

		show(AppUpdateState.UpToDate)
		compose.onNodeWithText("Later").assertDoesNotExist()
	}

	@Test
	fun `an installable release offers update and later`() {
		show(AppUpdateState.Available(version = "9.9.9", installable = true))

		compose.onNodeWithText("Hammer 9.9.9 is available.").assertIsDisplayed()
		compose.onNodeWithText("Update to 9.9.9").performClick()
		compose.onNodeWithText("Later").performClick()

		assertEquals(1, updates)
		assertEquals(1, dismissals)
	}

	@Test
	fun `a release this install cannot apply links to the release page`() {
		show(AppUpdateState.Available(version = "9.9.9", installable = false))

		compose.onNodeWithText("Update to 9.9.9").assertDoesNotExist()
		compose.onNodeWithText("Open Release").performClick()

		assertEquals(1, releasePages)
		assertEquals(0, updates)
	}

	@Test
	fun `a dismissed release hides the banner`() {
		show(AppUpdateState.Available(version = "9.9.9", installable = true, dismissed = true))

		compose.onNodeWithText("Hammer 9.9.9 is available.").assertDoesNotExist()
	}

	@Test
	fun `progress and failure are shown`() {
		show(AppUpdateState.Downloading(version = "9.9.9", fraction = 0.42f))
		compose.onNodeWithText("Downloading Hammer 9.9.9 (42%)").assertIsDisplayed()

		show(AppUpdateState.Failed(version = "9.9.9", reason = "HTTP 503"))
		compose.onNodeWithText("Could not update to 9.9.9: HTTP 503").assertIsDisplayed()
		compose.onNodeWithText("Retry").performClick()
		assertEquals(1, updates)
	}
}
