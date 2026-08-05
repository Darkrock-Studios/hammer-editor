import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandler
import com.darkrockstudios.apps.hammer.common.components.projectselection.ProjectSelection
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projectselection.ChangelogDialog
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ChangelogDialogTest {
	@get:Rule
	val compose = createComposeRule()

	private class FakeProjectSelection(
		state: ProjectSelection.ChangelogState,
	) : ProjectSelection {
		var dismissCount = 0
		var openReleaseCount = 0

		override val changelog: Value<ProjectSelection.ChangelogState> = MutableValue(state)

		override fun dismissChangelog() {
			dismissCount++
		}

		override fun openLatestRelease() {
			openReleaseCount++
		}

		override fun showChangelog() = Unit

		override val stack: Value<ChildStack<ProjectSelection.Config, ProjectSelection.Destination>> =
			mockk()
		override val navRailState: Value<ProjectSelection.NavRailState> =
			MutableValue(ProjectSelection.NavRailState(expanded = false))
		override val backHandler: BackHandler = mockk(relaxed = true)
		override fun isAtRoot() = false
		override fun onBack() = Unit
		override fun showLocation(location: ProjectSelection.Locations) = Unit
		override fun toggleNavRailExpanded() = Unit
	}

	private fun content(component: ProjectSelection): @Composable () -> Unit = {
		AppTheme(globalSettingsPreview) {
			ChangelogDialog(component)
		}
	}

	@Test
	fun `Dialog shows the baked version date and notes`() {
		val component = FakeProjectSelection(
			ProjectSelection.ChangelogState(
				visible = true,
				version = "3.7.2",
				date = "2026-7-27",
				notes = "[Improve]\n- Web: Redesign home page",
			)
		)
		compose.setContent(content(component))

		compose.onNodeWithText("3.7.2").assertIsDisplayed()
		compose.onNodeWithText("2026-7-27").assertIsDisplayed()
		compose.onNodeWithText("[Improve]\n- Web: Redesign home page").assertIsDisplayed()
	}

	@Test
	fun `Closing the dialog dismisses it`() {
		val component = FakeProjectSelection(
			ProjectSelection.ChangelogState(visible = true, version = "3.7.2", notes = "- A thing")
		)
		compose.setContent(content(component))

		compose.onNodeWithText("× CLOSE").performClick()

		assertEquals(1, component.dismissCount)
	}

	@Test
	fun `Release button opens the releases page`() {
		val component = FakeProjectSelection(
			ProjectSelection.ChangelogState(visible = true, version = "3.7.2", notes = "- A thing")
		)
		compose.setContent(content(component))

		compose.onNodeWithText("Open Release").performClick()

		assertEquals(1, component.openReleaseCount)
	}
}
