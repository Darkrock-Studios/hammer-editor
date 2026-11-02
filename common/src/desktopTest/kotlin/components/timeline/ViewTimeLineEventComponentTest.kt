package components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEventComponent
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ViewTimeLineEventComponentTest : TimelineComponentTestBase() {

	private val shownScenes = mutableListOf<SceneItem>()
	private val shownEntries = mutableListOf<EntryDef>()

	private fun newComponent(eventId: Int) = ViewTimeLineEventComponent(
		componentContext = context,
		projectDef = projectDef,
		eventId = eventId,
		onCloseEvent = {},
		addMenu = {},
		removeMenu = {},
		updateShouldClose = {},
		onShowGlobalSearchForTag = {},
		onShowScene = { shownScenes += it },
		onShowEntry = { shownEntries += it },
	)

	private fun TimelineResult.event(): TimeLineEvent = (this as TimelineResult.Ok).event!!

	@Test
	fun `a scene placement shows its chapter, excerpt and cast with people first`() = runTest(mainTestDispatcher) {
		givenProject()
		sceneMetadataRepository.storeMetadata(SceneMetadata(confirmedReferences = setOf(WONDERLAND, ALICE)), sceneId = 4)
		val placed = timelineService.placeScene(4, null, null, lane = "Alice", milestone = false).event()

		val component = newComponent(placed.id)
		context.resume()
		advanceUntilIdle()

		val state = component.state.value
		assertEquals(TimelineKind.SCENE, state.kind)
		val subject = assertIs<SubjectSummary.Scene>(state.subject)
		assertEquals("Chapter ID 2", subject.chapterPath)
		assertEquals("Content of scene id 4", subject.excerpt)
		assertEquals(listOf(ALICE, WONDERLAND), state.references.map { it.id })
		assertEquals("Scene ID 4", state.displayTitle)
		assertEquals(0, state.laneIndex)

		component.openSubject()
		assertEquals(listOf(4), shownScenes.map { it.id })
	}

	@Test
	fun `an event shows its references and edits them`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm", references = setOf(ALICE, 4))).event()

		val component = newComponent(event.id)
		context.resume()
		advanceUntilIdle()
		assertEquals(setOf(ALICE, 4), component.state.value.references.map { it.id }.toSet())

		component.beginEdit()
		component.addReference(catalogItem(WONDERLAND))
		component.removeReference(4)
		assertTrue(component.isEditingAndDirty())
		assertEquals(TimeLineEventError.NONE, component.saveEdit())
		advanceUntilIdle()

		assertEquals(setOf(ALICE, WONDERLAND), timeLineRepository.getTimelineEvent(event.id)?.references)
		assertEquals(setOf(ALICE, WONDERLAND), component.state.value.references.map { it.id }.toSet())
		assertFalse(component.state.value.isEditing)

		component.openReference(catalogItem(ALICE))
		advanceUntilIdle()
		assertEquals(listOf(ALICE), shownEntries.map { it.id })
	}

	@Test
	fun `saving an event stores its text, when, title, lane and milestone`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(content = "original", label = "Dawn")).event()

		val component = newComponent(event.id)
		context.resume()
		advanceUntilIdle()

		component.beginEdit()
		component.onEventTextChanged("updated content")
		component.onDateTextChanged("Dusk")
		component.onTitleChanged("Storm")
		component.onLaneChanged("Alice")
		component.onMilestoneChanged(true)
		assertEquals(TimeLineEventError.NONE, component.saveEdit())
		advanceUntilIdle()

		val stored = timeLineRepository.getTimelineEvent(event.id)!!
		assertEquals("updated content", stored.content)
		assertEquals("Dusk", stored.label)
		assertEquals("Storm", stored.title)
		assertEquals("Alice", stored.lane)
		assertTrue(stored.milestone)
		assertEquals(stored, component.state.value.event)
		assertFalse(component.state.value.isEditing)
	}

	@Test
	fun `a placement edits only its placement fields and when`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeEntry(TEA_PARTY, null, null, lane = null, milestone = false).event()

		val component = newComponent(placed.id)
		context.resume()
		advanceUntilIdle()
		assertIs<SubjectSummary.Entry>(component.state.value.subject)

		component.beginEdit()
		component.onLaneChanged("Hatter")
		component.onMilestoneChanged(true)
		component.onDateTextChanged("Six o'clock")
		assertEquals(TimeLineEventError.NONE, component.saveEdit())
		advanceUntilIdle()

		val stored = timeLineRepository.getTimelineEvent(placed.id)!!
		assertEquals("Hatter", stored.lane)
		assertTrue(stored.milestone)
		assertEquals("Six o'clock", stored.label)
		assertEquals(TimelineKind.ENTRY, stored.kind)
		assertEquals(TEA_PARTY, stored.subject)

		component.openSubject()
		assertEquals(listOf(TEA_PARTY), shownEntries.map { it.id })
	}

	@Test
	fun `a placement without its subject reports it missing`() = runTest(mainTestDispatcher) {
		givenProject()
		val orphan = timeLineRepository.createEvent(
			TimelineEventDraft(kind = TimelineKind.SCENE, subject = 999, cachedTitle = "Gone scene")
		)

		val component = newComponent(orphan.id)
		context.resume()
		advanceUntilIdle()

		assertEquals(SubjectSummary.Missing, component.state.value.subject)
		assertEquals("Gone scene", component.state.value.displayTitle)

		component.deleteEvent()
		assertEquals(emptyList(), timeLineRepository.currentEvents())
	}

	@Test
	fun `an event changed elsewhere refreshes the open view`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(content = "Event 0", label = "First date")).event()

		val component = newComponent(event.id)
		context.resume()
		advanceUntilIdle()
		assertEquals("Event 0", component.contentText.value)

		timelineService.updateEvent(event.copy(label = "server date", content = "server content"))
		advanceUntilIdle()

		assertEquals("server content", component.contentText.value)
		assertEquals("server date", component.dateText.value)
	}

	@Test
	fun `an event changed elsewhere replaces an untouched edit`() = runTest(mainTestDispatcher) {
		givenProject()
		// No label, which must not read as a dirty edit.
		val event = timelineService.createEvent(TimelineEventDraft(content = "Event 1")).event()

		val component = newComponent(event.id)
		context.resume()
		advanceUntilIdle()

		component.beginEdit()
		assertFalse(component.isEditingAndDirty())

		timelineService.updateEvent(event.copy(content = "server content"))
		advanceUntilIdle()

		assertTrue(component.state.value.isEditing)
		assertEquals("server content", component.contentText.value)
		assertFalse(component.isEditingAndDirty())
	}

	@Test
	fun `an event changed elsewhere while editing keeps the draft`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(content = "Event 0")).event()

		val component = newComponent(event.id)
		context.resume()
		advanceUntilIdle()

		component.beginEdit()
		component.onEventTextChanged("my draft")

		timelineService.updateEvent(event.copy(content = "server content"))
		advanceUntilIdle()

		assertEquals("server content", component.state.value.event?.content)
		assertEquals("my draft", component.contentText.value)

		component.discardEdit()
		assertEquals("server content", component.contentText.value)
	}
}
