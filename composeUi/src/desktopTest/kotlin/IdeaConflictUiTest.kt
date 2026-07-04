import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.sync.ideassync.IdeaConflict
import com.darkrockstudios.apps.hammer.common.projectsync.IdeaConflictUi
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class IdeaConflictUiTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private fun idea(content: String, title: String? = null) = StoryIdea(
		id = IdeaId("00000000-0000-0000-0000-000000000001"),
		created = Instant.parse("2026-07-01T12:00:00Z"),
		updated = Instant.parse("2026-07-01T12:00:00Z"),
		title = title,
		content = content,
	)

	@Test
	fun `Use remote resolves with the server copy`() {
		val local = idea("local words")
		val server = idea("server words")
		var resolved: StoryIdea? = null

		compose.setContent {
			IdeaConflictUi(
				conflict = IdeaConflict(local = local, server = server),
				compact = true,
				onResolve = { resolved = it },
			)
		}

		// The compact layout opens on the Remote tab, whose pane holds the only USE action.
		compose.onNodeWithText("USE").performClick()

		assertEquals(server, resolved)
	}

	@Test
	fun `Local edits ride along when using local`() {
		val local = idea("local words")
		val server = idea("server words")
		var resolved: StoryIdea? = null

		compose.setContent {
			IdeaConflictUi(
				conflict = IdeaConflict(local = local, server = server),
				compact = true,
				onResolve = { resolved = it },
			)
		}

		compose.onNodeWithText("LOCAL").performClick()
		compose.onNodeWithText("local words").performTextReplacement("merged words")
		compose.onNodeWithText("USE").performClick()

		assertEquals("merged words", resolved?.content)
		assertEquals(local.id, resolved?.id)
	}
}
