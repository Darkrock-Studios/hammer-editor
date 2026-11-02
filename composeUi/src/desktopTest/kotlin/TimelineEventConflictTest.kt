import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.base.http.ApiProjectEntity
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.components.projectsync.ProjectSynchronization
import com.darkrockstudios.apps.hammer.common.compose.WindowSizeClass
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.projectsync.LocalTimelineEvent
import com.darkrockstudios.apps.hammer.common.projectsync.TIMELINE_CONFLICT_START_TAG
import com.darkrockstudios.apps.hammer.common.projectsync.TimelineEventConflict
import com.darkrockstudios.apps.hammer.common.projectsync.buildLocalTimelineEvent
import com.darkrockstudios.apps.hammer.common.projectsync.toEdits
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineEventConflictTest : BaseTest() {

	@get:Rule
	val compose = createComposeRule()

	private val event = ApiProjectEntity.TimelineEventEntity(
		id = 5,
		order = 1,
		label = "teatime",
		title = "The croquet match",
		content = "The Queen calls for croquet.",
		tags = setOf("queen"),
		references = setOf(2),
		lane = "Court",
		milestone = true,
		start = StoryDate(mapOf("year" to 97L, "day" to 214L)),
	)

	private val placement = ApiProjectEntity.TimelineEventEntity(
		id = 4,
		order = 2,
		label = null,
		content = "",
		kind = TimelineKind.SCENE,
		subject = 12,
		cachedTitle = "Down the Rabbit-Hole",
		lane = "Alice",
		start = StoryDate(mapOf("year" to 97L, "day" to 210L)),
	)

	@Test
	fun `Unchanged edits resolve to the client copy`() {
		val local = buildLocalTimelineEvent(event, event.toEdits())

		assertEquals(event, assertIs<LocalTimelineEvent.Resolved>(local).entity)
	}

	@Test
	fun `Edits apply and blank optional fields clear`() {
		val edits = event.toEdits().copy(
			title = " ",
			label = "",
			content = "Merged words.",
			lane = "  Garden ",
			milestone = false,
			start = "year=97 day=215 hour=9",
			end = "year=97 day=216",
		)

		val resolved = assertIs<LocalTimelineEvent.Resolved>(buildLocalTimelineEvent(event, edits)).entity

		assertNull(resolved.title)
		assertNull(resolved.label)
		assertEquals("Merged words.", resolved.content)
		assertEquals("Garden", resolved.lane)
		assertEquals(false, resolved.milestone)
		assertEquals(StoryDate(mapOf("year" to 97L, "day" to 215L, "hour" to 9L)), resolved.start)
		assertEquals(StoryDate(mapOf("year" to 97L, "day" to 216L)), resolved.end)
		assertEquals(event.tags, resolved.tags)
		assertEquals(event.references, resolved.references)
	}

	@Test
	fun `A placement keeps its content and subject`() {
		val edits = placement.toEdits().copy(content = "typed into a placement", start = "")

		val resolved = assertIs<LocalTimelineEvent.Resolved>(buildLocalTimelineEvent(placement, edits)).entity

		assertEquals("", resolved.content)
		assertEquals(TimelineKind.SCENE, resolved.kind)
		assertEquals(12, resolved.subject)
		assertNull(resolved.start)
	}

	@Test
	fun `Unreadable dates are reported instead of resolving`() {
		val edits = event.toEdits().copy(start = "day two", end = "year=97")

		val local = buildLocalTimelineEvent(event, edits)

		assertEquals(LocalTimelineEvent.UnreadableDates(start = true, end = false), local)
	}

	@Test
	fun `Using local resolves a placement with its edited lane and start`() {
		val component = RecordingSync()
		showConflict(server = placement.copy(lane = "Wonderland"), client = placement, component = component)

		compose.onNodeWithText("LOCAL").performClick()
		compose.onNodeWithText("Alice").performTextReplacement("Queen's garden")
		compose.onNodeWithTag(TIMELINE_CONFLICT_START_TAG).performTextReplacement("year=97 day=211")
		compose.onNodeWithText("USE").performClick()

		assertEquals(
			placement.copy(lane = "Queen's garden", start = StoryDate(mapOf("year" to 97L, "day" to 211L))),
			component.resolved.single(),
		)
	}

	@Test
	fun `An unreadable start shows an error and does not resolve`() {
		val component = RecordingSync()
		showConflict(server = placement.copy(lane = "Wonderland"), client = placement, component = component)

		compose.onNodeWithText("LOCAL").performClick()
		compose.onNodeWithTag(TIMELINE_CONFLICT_START_TAG).performTextReplacement("the next morning")
		compose.onNodeWithText("USE").performClick()

		assertTrue(component.resolved.isEmpty())
		compose.onNodeWithText("unit=number pairs", substring = true, ignoreCase = true).assertExists()
	}

	@Test
	fun `A rule problem from the component is shown`() {
		val component = RecordingSync(
			error = ProjectSynchronization.EntityMergeError.TimelineEventMergeError(
				ruleProblem = "a placement cannot have content",
			)
		)
		showConflict(server = placement.copy(lane = "Wonderland"), client = placement, component = component)

		compose.onNodeWithText("LOCAL").performClick()
		compose.onNodeWithText("USE").performClick()

		compose.onNodeWithText("a placement cannot have content", ignoreCase = true).assertExists()
	}

	private fun showConflict(
		server: ApiProjectEntity.TimelineEventEntity,
		client: ApiProjectEntity.TimelineEventEntity,
		component: ProjectSynchronization,
	) {
		val compact = WindowSizeClass.calculateFromSize(widthPx = 420, heightPx = 900, density = Density(1f))
		compose.setContent {
			KoinApplicationPreview {
				TimelineEventConflict(
					entityConflict = ProjectSynchronization.EntityConflict.TimelineEventConflict(
						serverEvent = server,
						clientEvent = client,
					),
					component = component,
					screenCharacteristics = compact,
				)
			}
		}
	}

	private class RecordingSync(
		private val error: ProjectSynchronization.EntityMergeError? = null,
	) : ProjectSynchronization {
		val resolved = mutableListOf<ApiProjectEntity>()

		override val state = MutableValue(ProjectSynchronization.State())
		override fun syncProject(onComplete: (Boolean) -> Unit) {}
		override fun resolveConflict(resolvedEntity: ApiProjectEntity): ProjectSynchronization.EntityMergeError? {
			if (error == null) resolved += resolvedEntity
			return error
		}

		override fun resolveProjectDataConflict(resolved: ProjectData) {}
		override fun endSync() {}
		override fun cancelSync() {}
		override fun showLog(show: Boolean) {}
		override fun onUnauthorized() {}
		override fun resolveEntryRef(id: Int) = null
	}
}
