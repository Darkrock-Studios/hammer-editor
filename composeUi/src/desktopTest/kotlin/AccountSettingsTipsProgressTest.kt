import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.nux.NuxProgress
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.projectselection.accountSettingsComponent
import com.darkrockstudios.apps.hammer.common.projectselection.defaultAccountSettingsComponentState
import com.darkrockstudios.apps.hammer.common.projectselection.settings.AccountSettingsUi
import org.junit.Rule
import org.junit.Test

class AccountSettingsTipsProgressTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private fun showSettings(progress: NuxProgress) {
		val component = accountSettingsComponent(
			defaultAccountSettingsComponentState.copy(serverSetup = false, tipsProgress = progress)
		)
		compose.setContent {
			KoinApplicationPreview {
				AccountSettingsUi(component, rememberRootSnackbarHostState(), Modifier.size(600.dp, 3000.dp))
			}
		}
	}

	@Test
	fun `a single tip reads in the singular`() {
		showSettings(NuxProgress(seen = 0, total = 1))

		compose.onNodeWithText("Seen 0 of 1 tip").assertIsDisplayed()
	}

	@Test
	fun `several tips read in the plural`() {
		showSettings(NuxProgress(seen = 1, total = 2))

		compose.onNodeWithText("Seen 1 of 2 tips").assertIsDisplayed()
	}
}
