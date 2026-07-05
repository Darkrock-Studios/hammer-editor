import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.common.components.projectselection.storyideas.StoryIdeas
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.CResult
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ideasrepository.IdeaError
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.IDEAS_CREATE_FAB_TAG
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.StoryIdeasUi
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.ideaCardTag
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class StoryIdeasUiTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private class FakeStoryIdeas(ideas: List<StoryIdea>) : StoryIdeas {
		override val state: Value<StoryIdeas.State> = MutableValue(StoryIdeas.State(ideas = ideas))

		var createShownCount = 0
		var editedId: IdeaId? = null

		override fun showCreate() {
			createShownCount++
		}

		override fun editIdea(id: IdeaId) {
			editedId = id
		}

		override fun closeEditor() {}
		override fun suggestTags(prefix: String): List<String> = emptyList()
		override suspend fun createIdea(title: String?, content: String, tags: Set<String>) = IdeaError.NONE
		override suspend fun saveIdea(id: IdeaId, title: String?, content: String, tags: Set<String>) = IdeaError.NONE
		override suspend fun deleteIdea(id: IdeaId) {}
		override suspend fun archiveIdea(id: IdeaId) {}
		override suspend fun unarchiveIdea(id: IdeaId) {}
		override suspend fun promoteIdea(id: IdeaId): CResult<ProjectDef> =
			CResult.failure(Exception("fake"))
	}

	private fun idea(uuid: String, content: String, title: String? = null, archived: Boolean = false) = StoryIdea(
		id = IdeaId(uuid),
		created = Instant.parse("2026-07-01T12:00:00Z"),
		updated = Instant.parse("2026-07-01T12:00:00Z"),
		title = title,
		content = content,
		archived = if (archived) Instant.parse("2026-07-02T12:00:00Z") else null,
	)

	@Test
	fun `Idea cards render title and content`() {
		val component = FakeStoryIdeas(
			listOf(
				idea("00000000-0000-0000-0000-000000000001", "A courier of dreams", title = "The Courier"),
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
		val component = FakeStoryIdeas(emptyList())

		compose.setContent {
			StoryIdeasUi(component = component, rootSnackbar = rememberRootSnackbarHostState())
		}

		compose.onNodeWithTag(IDEAS_CREATE_FAB_TAG).performClick()

		assertEquals(1, component.createShownCount)
	}

	@Test
	fun `Clicking a card opens it for editing`() {
		val uuid = "00000000-0000-0000-0000-000000000002"
		val component = FakeStoryIdeas(listOf(idea(uuid, "tap me")))

		compose.setContent {
			StoryIdeasUi(component = component, rootSnackbar = rememberRootSnackbarHostState())
		}

		compose.onNodeWithTag(ideaCardTag(uuid)).performClick()

		assertEquals(IdeaId(uuid), component.editedId)
	}

	@Test
	fun `Archived ideas are hidden from the active list`() {
		val component = FakeStoryIdeas(
			listOf(
				idea("00000000-0000-0000-0000-000000000003", "active spark"),
				idea("00000000-0000-0000-0000-000000000004", "archived spark", archived = true),
			)
		)

		compose.setContent {
			StoryIdeasUi(component = component, rootSnackbar = rememberRootSnackbarHostState())
		}

		compose.onNodeWithText("active spark").assertIsDisplayed()
		compose.onNodeWithText("archived spark").assertDoesNotExist()
	}
}
