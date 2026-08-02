import com.darkrockstudios.apps.hammer.base.IdeaId
import com.darkrockstudios.apps.hammer.base.http.storyideas.StoryIdea
import com.darkrockstudios.apps.hammer.common.data.notesrepository.note.NoteContent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.notes.noteMatchesQuery
import com.darkrockstudios.apps.hammer.common.projectselection.storyideas.ideaMatchesQuery
import com.darkrockstudios.apps.hammer.common.timeline.eventMatchesQuery
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Each screen's match predicate, covering which fields it consults and how. A screen matching one of
 * its own fields the wrong way is invisible to the shared helper's tests.
 *
 * These assert the predicates, not the composables that call them; `TimeLineOverviewUiTest` drives a
 * screen's search field end to end.
 */
class SearchFilterTest {

	private fun note(content: String) =
		NoteContent(id = 1, created = Clock.System.now(), content = content)

	private fun event(content: String, date: String? = null) =
		TimeLineEvent(id = 1, order = 0, date = date, content = content)

	private fun idea(content: String, title: String? = null) = StoryIdea(
		id = IdeaId("00000000-0000-0000-0000-000000000001"),
		created = Instant.parse("2026-07-01T12:00:00Z"),
		updated = Instant.parse("2026-07-01T12:00:00Z"),
		title = title,
		content = content,
		archived = null,
	)

	@Test
	fun `notes match a query across a stored escape`() {
		assertTrue(noteMatchesQuery(note("A well\\-known secret"), "well-known"))
		assertFalse(noteMatchesQuery(note("A well\\-known secret"), "unrelated"))
	}

	@Test
	fun `timeline events match on the body across a stored escape`() {
		assertTrue(eventMatchesQuery(event("A well\\-known war"), "well-known"))
	}

	@Test
	fun `timeline events match on the plain-text date as stored`() {
		assertTrue(eventMatchesQuery(event("A long war", date = "1990-2000"), "1990-2000"))
		// The date is not Markdown, so it is matched exactly as the user typed it.
		assertTrue(eventMatchesQuery(event("A long war", date = "1990\\-2000"), "1990\\-2000"))
	}

	@Test
	fun `story ideas match on the body across a stored escape`() {
		assertTrue(ideaMatchesQuery(idea("A well\\-known twist"), "well-known"))
	}

	@Test
	fun `story ideas match on the plain-text title`() {
		assertTrue(ideaMatchesQuery(idea("body text", title = "The well-known twist"), "well-known"))
		assertFalse(ideaMatchesQuery(idea("body text", title = "Something else"), "well-known"))
	}

	@Test
	fun `literal markup is not rewritten by any screen filter`() {
		assertTrue(noteMatchesQuery(note("The cost is 5*4"), "5*4"))
		assertTrue(eventMatchesQuery(event("the user_name field"), "user_name"))
		assertTrue(ideaMatchesQuery(idea("a **Chapter** heading"), "**Chapter**"))
	}
}
