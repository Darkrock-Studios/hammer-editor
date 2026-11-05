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
import com.darkrockstudios.apps.hammer.base.timeline.StoryDateProblem
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDateField
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDateProblem
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineRuleProblem
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

private const val TIMELINE_ROW = "TIMELINE · CHANGED"

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

	private val evening = TimelineDef(id = "b1e4c0de", name = "Before the evening")

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
		val local = buildLocalTimelineEvent(event, event.toEdits(), server = event)

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

		val resolved = assertIs<LocalTimelineEvent.Resolved>(buildLocalTimelineEvent(event, edits, server = event)).entity

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

		val resolved = assertIs<LocalTimelineEvent.Resolved>(buildLocalTimelineEvent(placement, edits, server = placement)).entity

		assertEquals("", resolved.content)
		assertEquals(TimelineKind.SCENE, resolved.kind)
		assertEquals(12, resolved.subject)
		assertNull(resolved.start)
	}

	@Test
	fun `The chosen timeline replaces the client's`() {
		val edits = event.toEdits().copy(timeline = evening.id)

		val resolved = assertIs<LocalTimelineEvent.Resolved>(buildLocalTimelineEvent(event, edits, server = event)).entity

		assertEquals(evening.id, resolved.timeline)
	}

	@Test
	fun `Picking the server's timeline takes the server's order there`() {
		val server = event.copy(order = 7, timeline = evening.id)

		val onServers = buildLocalTimelineEvent(event, event.toEdits().copy(timeline = evening.id), server)
		val onOwn = buildLocalTimelineEvent(event, event.toEdits(), server)

		assertEquals(7, assertIs<LocalTimelineEvent.Resolved>(onServers).entity.order)
		assertEquals(event.order, assertIs<LocalTimelineEvent.Resolved>(onOwn).entity.order)
	}

	@Test
	fun `Unreadable dates are reported instead of resolving`() {
		val edits = event.toEdits().copy(start = "day two", end = "year=97")

		val local = buildLocalTimelineEvent(event, edits, server = event)

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
				ruleProblem = TimelineRuleProblem.PlacementHasContent,
			)
		)
		showConflict(server = placement.copy(lane = "Wonderland"), client = placement, component = component)

		compose.onNodeWithText("LOCAL").performClick()
		compose.onNodeWithText("USE").performClick()

		compose.onNodeWithText("A placement cannot have content.", ignoreCase = true).assertExists()
	}

	@Test
	fun `A date problem from the component is shown`() {
		val component = RecordingSync(
			error = ProjectSynchronization.EntityMergeError.TimelineEventMergeError(
				dateProblem = TimelineDateProblem.Invalid(
					TimelineDateField.START,
					StoryDateProblem.OutOfRange("day", 400),
				),
			)
		)
		showConflict(server = event.copy(lane = "Wonderland"), client = event, component = component)

		compose.onNodeWithText("LOCAL").performClick()
		compose.onNodeWithText("USE").performClick()

		compose.onNodeWithText("Start has day out of range: 400", ignoreCase = true).assertExists()
	}

	@Test
	fun `A taken placement from the component is shown`() {
		val component = RecordingSync(
			error = ProjectSynchronization.EntityMergeError.TimelineEventMergeError(placementTaken = true)
		)
		showConflict(server = placement.copy(timeline = evening.id), client = placement, component = component)

		compose.onNodeWithText("LOCAL").performClick()
		compose.onNodeWithText("BEFORE THE EVENING").performClick()
		compose.onNodeWithText("USE").performClick()

		compose.onNodeWithText("already placed on that timeline", substring = true, ignoreCase = true).assertExists()
	}

	@Test
	fun `No timeline row is shown when both sides share a timeline`() {
		showConflict(
			server = placement.copy(lane = "Wonderland", timeline = evening.id),
			client = placement.copy(timeline = evening.id),
			component = RecordingSync(),
		)

		compose.onNodeWithText(TIMELINE_ROW).assertDoesNotExist()
		compose.onNodeWithText("LOCAL").performClick()
		compose.onNodeWithText(TIMELINE_ROW).assertDoesNotExist()
	}

	@Test
	fun `Each side's timeline is named and an unknown id is a deleted timeline`() {
		showConflict(
			server = placement.copy(timeline = "0badf00d"),
			client = placement,
			component = RecordingSync(),
		)

		compose.onNodeWithText(TIMELINE_ROW).assertExists()
		compose.onNodeWithText("Deleted timeline").assertExists()
		compose.onNodeWithText("LOCAL").performClick()
		compose.onNodeWithText(TIMELINE_ROW).assertExists()
		compose.onNodeWithText("DELETED TIMELINE").assertExists()
		compose.onNodeWithText("MAIN").assertExists()
	}

	@Test
	fun `Using local keeps the client's timeline unless the other side is picked`() {
		val component = RecordingSync()
		showConflict(server = placement.copy(timeline = evening.id), client = placement, component = component)

		compose.onNodeWithText("LOCAL").performClick()
		compose.onNodeWithText("USE").performClick()
		compose.onNodeWithText("BEFORE THE EVENING").performClick()
		compose.onNodeWithText("USE").performClick()

		assertEquals(listOf(null, evening.id), component.resolved.map { (it as ApiProjectEntity.TimelineEventEntity).timeline })
	}

	private fun showConflict(
		server: ApiProjectEntity.TimelineEventEntity,
		client: ApiProjectEntity.TimelineEventEntity,
		component: ProjectSynchronization,
		liveTimelines: List<TimelineDef> = listOf(TimelineDef.DEFAULT, evening),
	) {
		val compact = WindowSizeClass.calculateFromSize(widthPx = 420, heightPx = 900, density = Density(1f))
		compose.setContent {
			KoinApplicationPreview {
				TimelineEventConflict(
					entityConflict = ProjectSynchronization.EntityConflict.TimelineEventConflict(
						serverEvent = server,
						clientEvent = client,
						liveTimelines = liveTimelines,
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
