import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.IDEAS_CREATE_FAB_TAG
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.IDEAS_EDITOR_TITLE_TAG
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.StoryIdeasUi
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.ideaCardTag
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class StoryIdeasUiTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	@Test
	fun `Idea cards render title and content`() {
		val component = FakeStoryIdeas(
			listOf(
				storyIdea("00000000-0000-0000-0000-000000000001", "A courier of dreams", title = "The Courier"),
			)
		)

		compose.setContent {
			StoryIdeasUi(component = component, rootSnackbar = rememberRootSnackbarHostState())
		}

		compose.onNodeWithText("The Courier").assertIsDisplayed()
		compose.onNodeWithText("A courier of dreams").assertIsDisplayed()
	}

	@Test
	fun `FAB opens the create editor`() {
		val component = FakeStoryIdeas()

		compose.setContent {
			KoinApplicationPreview {
				StoryIdeasUi(component = component, rootSnackbar = rememberRootSnackbarHostState())
			}
		}

		compose.onNodeWithTag(IDEAS_CREATE_FAB_TAG).performClick()

		assertEquals(1, component.createShownCount)
	}

	@Test
	fun `Clicking a card opens it for editing`() {
		val uuid = "00000000-0000-0000-0000-000000000002"
		val component = FakeStoryIdeas(listOf(storyIdea(uuid, "tap me")))

		compose.setContent {
			KoinApplicationPreview {
				StoryIdeasUi(component = component, rootSnackbar = rememberRootSnackbarHostState())
			}
		}

		compose.onNodeWithTag(ideaCardTag(uuid)).performClick()

		assertEquals(IdeaId(uuid), component.editedId)
	}

	@Test
	fun `Archived ideas are hidden from the active list`() {
		val component = FakeStoryIdeas(
			listOf(
				storyIdea("00000000-0000-0000-0000-000000000003", "active spark"),
				storyIdea("00000000-0000-0000-0000-000000000004", "archived spark", archived = true),
			)
		)

		compose.setContent {
			StoryIdeasUi(component = component, rootSnackbar = rememberRootSnackbarHostState())
		}

		compose.onNodeWithText("active spark").assertIsDisplayed()
		compose.onNodeWithText("archived spark").assertDoesNotExist()
	}

	@Test
	fun `Typing in the editor writes through to the component`() {
		val component = FakeStoryIdeas()
		component.showCreate()

		compose.setContent {
			KoinApplicationPreview {
				StoryIdeasUi(component = component, rootSnackbar = rememberRootSnackbarHostState())
			}
		}

		compose.onNodeWithTag(IDEAS_EDITOR_TITLE_TAG).performTextInput("Tides")

		assertEquals("Tides", component.state.value.draft?.title)
	}
}
