import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.IDEAS_EDITOR_TITLE_TAG
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.StoryIdeasUi
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Regression cover for #885. An Android configuration change tears the composition down and builds
 * it again while the Decompose component (created with `retainedComponent`) lives on, so anything
 * the editor keeps in a plain `remember` is gone by the time the new composition runs.
 *
 * Bumping the `key` below reproduces that half of a rotation: the whole subtree is disposed and
 * recomposed against the same component. `androidx.compose.ui.test.StateRestorationTester` would
 * be the closer fit, but it is an unimplemented stub on the desktop target.
 */
class StoryIdeasRotationTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	@Test
	fun `An unsaved draft survives the composition being recreated`() {
		val component = FakeStoryIdeas()
		component.showCreate()
		val epoch = mutableStateOf(0)
		var buildCount = 0

		compose.setContent {
			// Koin stays outside the key: its teardown would otherwise race the rebuilt subtree.
			KoinApplicationPreview {
				key(epoch.value) {
					remember { buildCount++ }
					StoryIdeasUi(component = component, rootSnackbar = rememberRootSnackbarHostState())
				}
			}
		}

		compose.onNodeWithTag(IDEAS_EDITOR_TITLE_TAG).performTextInput("Tides")

		epoch.value++
		compose.waitForIdle()

		assertEquals(2, buildCount, "The subtree should have been thrown away and built again")
		compose.onNodeWithTag(IDEAS_EDITOR_TITLE_TAG).assertTextEquals("Tides")
	}
}
