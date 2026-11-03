package components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineLimits
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEventComponent
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.encyclopediarepository.entry.EntryDef
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogItem
import com.darkrockstudios.apps.hammer.common.data.entitycatalog.CatalogKind
import com.darkrockstudios.apps.hammer.common.data.imagesrepository.image.ImageDef
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.SubjectSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.data.tree.NodeCoordinates
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import io.mockk.every
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import utils.TestComponentContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ViewTimeLineEventComponentTest : TimelineComponentTestBase() {

	private val shownScenes = mutableListOf<SceneItem>()
	private val shownEntries = mutableListOf<EntryDef>()
	private val searchedTags = mutableListOf<String>()
	private val addedMenus = mutableListOf<MenuDescriptor>()
	private val removedMenus = mutableListOf<String>()
	private var closes = 0
	private var shouldCloseUpdates = 0

	private fun newComponent(eventId: Int, componentContext: TestComponentContext = context) =
		ViewTimeLineEventComponent(
			componentContext = componentContext,
			projectDef = projectDef,
			eventId = eventId,
			onCloseEvent = { closes++ },
			addMenu = { addedMenus += it },
			removeMenu = { removedMenus += it },
			updateShouldClose = { shouldCloseUpdates++ },
			onShowGlobalSearchForTag = { searchedTags += it },
			onShowScene = { shownScenes += it },
			onShowEntry = { shownEntries += it },
		)

	private fun TestScope.opened(eventId: Int): ViewTimeLineEventComponent {
		val component = newComponent(eventId)
		context.resume()
		advanceUntilIdle()
		return component
	}

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

	@Test
	fun `on a dated timeline an edit stores the drafted start and end`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		val placed = timelineService.placeScene(4, null, null, lane = null, milestone = false).event()
		timelineService.switchToDated(CalendarDef())

		val component = newComponent(event.id)
		context.resume()
		advanceUntilIdle()
		assertEquals(TimelineMode.DATED, component.state.value.mode)
		assertEquals(event.id, component.eventId)

		val start = StoryDate(mapOf("year" to 3L, "day" to 10L))
		val end = StoryDate(mapOf("year" to 3L, "day" to 12L))
		component.beginEdit()
		component.onStartChanged(start)
		component.onEndChanged(end)
		assertTrue(component.isEditingAndDirty())
		assertEquals(TimeLineEventError.NONE, component.saveEdit())

		val stored = timeLineRepository.getTimelineEvent(event.id)!!
		assertEquals(start, stored.start)
		assertEquals(end, stored.end)

		context.destroy()
		val placementContext = TestComponentContext.create()
		val placement = newComponent(placed.id, placementContext)
		placementContext.resume()
		advanceUntilIdle()

		placement.beginEdit()
		placement.onStartChanged(start)
		placement.onDateTextChanged("Night")
		assertEquals(TimeLineEventError.NONE, placement.saveEdit())

		val storedPlacement = timeLineRepository.getTimelineEvent(placed.id)!!
		assertEquals(start, storedPlacement.start)
		assertEquals("Night", storedPlacement.label)
		placementContext.destroy()
	}

	@Test
	fun `a dated timeline refuses to save an edit without a start`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		timelineService.switchToDated(CalendarDef())

		val component = newComponent(event.id)
		context.resume()
		advanceUntilIdle()

		component.beginEdit()
		component.onStartChanged(null)

		assertEquals(TimeLineEventError.DATE_REQUIRED, component.saveEdit())
		assertEquals(StoryDate(mapOf("year" to 0L)), timeLineRepository.getTimelineEvent(event.id)!!.start)
	}

	@Test
	fun `renaming the placed scene refreshes the open view`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeScene(4, null, null, lane = null, milestone = false).event()

		val component = newComponent(placed.id)
		context.resume()
		advanceUntilIdle()
		assertEquals("Scene ID 4", component.state.value.displayTitle)

		sceneRepository.renameScene(sceneRepository.getSceneItemFromId(4)!!, "Down the Hole")
		advanceUntilIdle()

		assertEquals("Down the Hole", component.state.value.displayTitle)
		assertEquals("Down the Hole", assertIs<SubjectSummary.Scene>(component.state.value.subject).scene.name)
	}

	@Test
	fun `a change to the placed scene's cast refreshes the open view`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeScene(4, null, null, lane = null, milestone = false).event()

		val component = newComponent(placed.id)
		context.resume()
		advanceUntilIdle()
		assertEquals(emptyList(), component.state.value.references)

		sceneMetadataRepository.storeMetadata(SceneMetadata(confirmedReferences = setOf(ALICE)), sceneId = 4)
		advanceUntilIdle()

		assertEquals(listOf(ALICE), component.state.value.references.map { it.id })
	}

	@Test
	fun `editing the placed entry refreshes the open view`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeEntry(TEA_PARTY, null, null, lane = null, milestone = false).event()

		val component = newComponent(placed.id)
		context.resume()
		advanceUntilIdle()

		val def = encyclopediaRepository.findEntryDef(TEA_PARTY)!!
		encyclopediaRepository.updateEntry(
			oldEntryDef = def,
			name = def.name,
			text = "The March Hare spills the milk.",
			tags = emptySet(),
			excludeFromDictionary = false,
			images = emptyList(),
		)
		advanceUntilIdle()

		val subject = assertIs<SubjectSummary.Entry>(component.state.value.subject)
		assertEquals("The March Hare spills the milk.", subject.excerpt)
	}

	// region Editing

	@Test
	fun `saving an event stores its tags`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm", tags = setOf("weather"))).event()
		val component = opened(event.id)

		component.beginEdit()
		component.onTagsChanged(setOf("weather", "night"))
		assertTrue(component.isEditingAndDirty())
		assertEquals(TimeLineEventError.NONE, component.saveEdit())
		advanceUntilIdle()

		assertEquals(setOf("weather", "night"), timeLineRepository.getTimelineEvent(event.id)?.tags)
		assertEquals(setOf("weather", "night"), component.state.value.tags)
	}

	@Test
	fun `each edit tells the host to recheck whether it may close`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		val component = opened(event.id)
		val before = shouldCloseUpdates

		component.beginEdit()
		component.onEventTextChanged("text")
		component.onDateTextChanged("Dusk")
		component.onTagsChanged(setOf("night"))
		component.onTitleChanged("Gale")
		component.onLaneChanged("Alice")
		component.onMilestoneChanged(true)
		component.onStartChanged(null)
		component.onEndChanged(null)
		component.addReference(catalogItem(ALICE))
		component.removeReference(ALICE)

		assertEquals(before + 10, shouldCloseUpdates)
	}

	@Test
	fun `an event cannot reference itself`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		val component = opened(event.id)

		component.beginEdit()
		component.addReference(CatalogItem(CatalogKind.Event, event.id, "Storm"))

		assertEquals(emptySet(), component.state.value.referenceIds)
		assertFalse(component.isEditingAndDirty())
	}

	@Test
	fun `discarding an edit puts back the stored event`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(
			TimelineEventDraft(
				title = "Storm",
				content = "Thunder",
				label = "Dusk",
				lane = "Alice",
				tags = setOf("weather"),
				references = setOf(ALICE),
			)
		).event()
		val component = opened(event.id)

		component.beginEdit()
		component.onTitleChanged("Calm")
		component.onEventTextChanged("Quiet")
		component.onDateTextChanged("Dawn")
		component.onLaneChanged("Bob")
		component.onMilestoneChanged(true)
		component.onTagsChanged(setOf("sunny"))
		component.addReference(catalogItem(WONDERLAND))
		component.discardEdit()

		val state = component.state.value
		assertFalse(state.isEditing)
		assertEquals("Storm", state.title)
		assertEquals("Thunder", component.contentText.value)
		assertEquals("Dusk", component.dateText.value)
		assertEquals("Alice", state.lane)
		assertFalse(state.milestone)
		assertEquals(setOf("weather"), state.tags)
		assertEquals(listOf(ALICE), state.references.map { it.id })
		assertEquals(event, timeLineRepository.getTimelineEvent(event.id))
	}

	@Test
	fun `confirm discard asks only about a dirty edit`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		val component = opened(event.id)

		component.beginEdit()
		component.confirmDiscard()
		assertFalse(component.state.value.isEditing)
		assertFalse(component.state.value.confirmDiscard)

		component.beginEdit()
		component.onTitleChanged("Calm")
		component.confirmDiscard()
		assertTrue(component.state.value.confirmDiscard)
		assertTrue(component.state.value.isEditing)

		component.cancelDiscard()
		assertFalse(component.state.value.confirmDiscard)
		assertEquals("Calm", component.state.value.title)

		component.confirmDiscard()
		component.discardEdit()
		assertFalse(component.state.value.confirmDiscard)
		assertFalse(component.state.value.isEditing)
		assertEquals("Storm", component.state.value.title)
	}

	@Test
	fun `confirm close asks only about a dirty edit`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		val component = opened(event.id)

		component.beginEdit()
		component.onTitleChanged("Calm")
		component.confirmClose()
		assertTrue(component.state.value.confirmClose)
		assertEquals(0, closes)

		component.cancelClose()
		assertFalse(component.state.value.confirmClose)
		assertTrue(component.state.value.isEditing)

		component.closeEvent()
		assertEquals(1, closes)
		assertFalse(component.state.value.isEditing)
		assertEquals("Storm", component.state.value.title)

		component.beginEdit()
		component.confirmClose()
		assertEquals(2, closes)
		assertFalse(component.state.value.confirmClose)
	}

	@Test
	fun `back while editing leaves a clean edit and asks about a dirty one`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		val component = opened(event.id)
		assertFalse(context.back())

		component.beginEdit()
		assertTrue(context.back())
		assertFalse(component.state.value.isEditing)

		component.beginEdit()
		component.onTitleChanged("Calm")
		assertTrue(context.back())
		assertTrue(component.state.value.confirmDiscard)
		assertTrue(component.state.value.isEditing)
	}

	@Test
	fun `an edit in progress survives process death`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm", content = "Thunder")).event()
		val component = opened(event.id)

		component.beginEdit()
		component.onTitleChanged("Calm")
		component.onEventTextChanged("Quiet")
		component.onDateTextChanged("Dawn")
		component.addReference(catalogItem(ALICE))

		val restoredContext = context.saveAndRecreate()
		val restored = newComponent(event.id, restoredContext)
		assertTrue(restored.isEditingAndDirty())
		restoredContext.resume()
		advanceUntilIdle()

		val state = restored.state.value
		assertTrue(state.isEditing)
		assertEquals("Calm", state.title)
		assertEquals("Quiet", restored.contentText.value)
		assertEquals("Dawn", restored.dateText.value)
		assertEquals(setOf(ALICE), state.referenceIds)
		assertTrue(restored.isEditingAndDirty())
	}

	// endregion

	// region Invalid saves

	@Test
	fun `a save the service refuses keeps editing and stores nothing`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		val component = opened(event.id)

		component.beginEdit()
		component.addReference(CatalogItem(CatalogKind.Scene, 999, "Ghost"))
		assertEquals(TimeLineEventError.REFERENCE_NOT_FOUND, component.saveEdit())
		assertTrue(component.state.value.isEditing)

		component.removeReference(999)
		component.onTitleChanged("x".repeat(TimelineLimits.MAX_TITLE + 1))
		assertEquals(TimeLineEventError.TITLE_TOO_LONG, component.saveEdit())
		assertTrue(component.state.value.isEditing)
		assertEquals(event, timeLineRepository.getTimelineEvent(event.id))
	}

	@Test
	fun `a dated save with the end before the start is refused`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		timelineService.switchToDated(CalendarDef())
		val component = opened(event.id)

		component.beginEdit()
		component.onStartChanged(StoryDate(mapOf("year" to 5L, "day" to 10L)))
		component.onEndChanged(StoryDate(mapOf("year" to 4L)))

		assertEquals(TimeLineEventError.END_BEFORE_START, component.saveEdit())
		assertTrue(component.state.value.isEditing)
		assertNull(timeLineRepository.getTimelineEvent(event.id)!!.end)
	}

	@Test
	fun `saving with no stored event reports it missing`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = opened(4040)

		component.beginEdit()
		assertTrue(component.isEditingAndDirty())
		assertEquals(TimeLineEventError.EVENT_NOT_FOUND, component.saveEdit())
	}

	@Test
	fun `a placement's invalid lane is refused`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeScene(4, null, null, lane = null, milestone = false).event()
		val component = opened(placed.id)

		component.beginEdit()
		component.onLaneChanged("x".repeat(TimelineLimits.MAX_LANE + 1))

		assertEquals(TimeLineEventError.LANE_INVALID, component.saveEdit())
		assertTrue(component.state.value.isEditing)
	}

	// endregion

	// region Placements

	@Test
	fun `a scene placement edits its placement fields and leaves the scene alone`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeScene(4, null, null, lane = null, milestone = false).event()
		val component = opened(placed.id)

		component.beginEdit()
		component.onTitleChanged("Not a placement field")
		component.onEventTextChanged("Nor this")
		assertFalse(component.isEditingAndDirty())

		component.onLaneChanged("Rabbit")
		component.onMilestoneChanged(true)
		component.onDateTextChanged("Noon")
		assertTrue(component.isEditingAndDirty())
		assertEquals(TimeLineEventError.NONE, component.saveEdit())
		advanceUntilIdle()

		val stored = timeLineRepository.getTimelineEvent(placed.id)!!
		assertEquals("Rabbit", stored.lane)
		assertTrue(stored.milestone)
		assertEquals("Noon", stored.label)
		assertNull(stored.title)
		assertEquals("", stored.content)
		assertEquals("Scene ID 4", sceneRepository.getSceneItemFromId(4)?.name)
		assertEquals("Scene ID 4", assertIs<SubjectSummary.Scene>(component.state.value.subject).scene.name)
		assertEquals(listOf("Rabbit"), component.state.value.lanes)
		assertEquals(0, component.state.value.laneIndex)
	}

	@Test
	fun `an entry placement on a dated timeline stores its drafted dates`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeEntry(TEA_PARTY, null, null, lane = null, milestone = false).event()
		timelineService.switchToDated(CalendarDef())
		val component = opened(placed.id)

		val start = StoryDate(mapOf("year" to 2L, "day" to 3L))
		val end = StoryDate(mapOf("year" to 2L, "day" to 5L))
		component.beginEdit()
		component.onStartChanged(start)
		component.onEndChanged(end)
		assertEquals(TimeLineEventError.NONE, component.saveEdit())
		advanceUntilIdle()

		val stored = timeLineRepository.getTimelineEvent(placed.id)!!
		assertEquals(start, stored.start)
		assertEquals(end, stored.end)
		assertEquals(start, component.state.value.start)
		assertEquals("Tea Party", encyclopediaRepository.findEntryDef(TEA_PARTY)?.name)
	}

	@Test
	fun `a placed entry shows its primary image`() = runTest(mainTestDispatcher) {
		givenProject()
		val image = ImageDef(7, "rabbit", "png")
		every { imagesService.findImage(7) } returns image
		every { imagesService.imagePath(image) } returns HPath("/images/7.png", "7.png", true)
		every { imagesService.cacheVersion(image) } returns "v1"
		encyclopediaRepository.addPrimaryImage(encyclopediaRepository.findEntryDef(TEA_PARTY)!!, 7)
		val placed = timelineService.placeEntry(TEA_PARTY, null, null, lane = null, milestone = false).event()

		val component = opened(placed.id)

		val shown = assertNotNull(component.state.value.subjectImage)
		assertEquals("rabbit", shown.name)
		assertEquals("/images/7.png", shown.path)
		assertEquals("v1", shown.version)
	}

	// endregion

	// region Opening

	@Test
	fun `opening a referenced scene shows it, and a gone one does nothing`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm", references = setOf(4))).event()
		val component = opened(event.id)

		component.openReference(catalogItem(4))
		component.openReference(CatalogItem(CatalogKind.Scene, 999, "Ghost"))
		component.openReference(CatalogItem(CatalogKind.Entry, 998, "Ghost"))
		component.openReference(CatalogItem(CatalogKind.Note, 1, "A note"))
		advanceUntilIdle()

		assertEquals(listOf(4), shownScenes.map { it.id })
		assertEquals(emptyList(), shownEntries)
	}

	@Test
	fun `opening the subject of an event, a missing or an archived placement does nothing`() =
		runTest(mainTestDispatcher) {
			givenProject()
			val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
			val placed = timelineService.placeScene(4, null, null, lane = null, milestone = false).event()
			val orphan = timeLineRepository.createEvent(TimelineEventDraft(kind = TimelineKind.ENTRY, subject = 999))
			sceneRepository.archiveScene(sceneRepository.getSceneItemFromId(4)!!)

			for (id in listOf(event.id, placed.id, orphan.id)) {
				val componentContext = TestComponentContext.create()
				val component = newComponent(id, componentContext)
				componentContext.resume()
				advanceUntilIdle()
				component.openSubject()
				componentContext.destroy()
			}

			assertEquals(emptyList(), shownScenes)
			assertEquals(emptyList(), shownEntries)
		}

	// endregion

	// region Delete

	@Test
	fun `deleting an event asks first, removes it and closes the view`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		val component = opened(event.id)

		component.startDeleteEvent()
		assertTrue(component.state.value.confirmDelete)
		component.endDeleteEvent()
		assertFalse(component.state.value.confirmDelete)

		component.startDeleteEvent()
		component.deleteEvent()
		advanceUntilIdle()

		assertEquals(emptyList(), timeLineRepository.currentEvents())
		assertFalse(component.state.value.confirmDelete)
		assertEquals(1, closes)
	}

	@Test
	fun `deleting a placement leaves its scene or entry alone`() = runTest(mainTestDispatcher) {
		givenProject()
		val scenePlacement = timelineService.placeScene(4, null, null, lane = null, milestone = false).event()
		val entryPlacement = timelineService.placeEntry(TEA_PARTY, null, null, lane = null, milestone = false).event()

		for (id in listOf(scenePlacement.id, entryPlacement.id)) {
			val componentContext = TestComponentContext.create()
			val component = newComponent(id, componentContext)
			componentContext.resume()
			advanceUntilIdle()
			component.deleteEvent()
			componentContext.destroy()
		}

		assertEquals(emptyList(), timeLineRepository.currentEvents())
		assertEquals("Scene ID 4", sceneRepository.getSceneItemFromId(4)?.name)
		assertEquals("Content of scene id 4", sceneRepository.loadSceneMarkdownRaw(sceneRepository.getSceneItemFromId(4)!!))
		assertEquals("Tea Party", encyclopediaRepository.findEntryDef(TEA_PARTY)?.name)
		assertEquals(2, closes)
	}

	@Test
	fun `deleting an event that is already gone keeps the view open`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		val component = opened(event.id)
		timelineService.deleteEvent(event.id)
		advanceUntilIdle()
		assertNull(component.state.value.event)

		component.startDeleteEvent()
		component.deleteEvent()

		assertFalse(component.state.value.confirmDelete)
		assertEquals(0, closes)
	}

	@Test
	fun `the delete menu is offered while the view is shown`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		val component = opened(event.id)

		val menu = addedMenus.single()
		val delete = menu.items.single()
		assertEquals(setOf(delete), component.state.value.menuItems)
		delete.action(delete.id)
		assertTrue(component.state.value.confirmDelete)

		context.stop()
		assertEquals(listOf(menu.id), removedMenus)
		assertEquals(emptySet(), component.state.value.menuItems)
	}

	// endregion

	// region Tags and lanes

	@Test
	fun `removing a tag outside an edit stores it at once`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm", tags = setOf("weather", "night"))).event()
		val component = opened(event.id)

		component.removeTag("weather")
		component.removeTag("unknown")
		assertEquals(setOf("night"), timeLineRepository.getTimelineEvent(event.id)?.tags)
		assertEquals(setOf("night"), component.state.value.tags)

		component.beginEdit()
		component.removeTag("night")
		assertEquals(setOf("night"), timeLineRepository.getTimelineEvent(event.id)?.tags)

		component.showGlobalSearchForTag("night")
		assertEquals(listOf("night"), searchedTags)
	}

	@Test
	fun `lane suggestions come from the lanes in use`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = timelineService.createEvent(TimelineEventDraft(title = "Storm", lane = "Alice")).event()
		timelineService.createEvent(TimelineEventDraft(title = "Tea", lane = "Malice"))
		timelineService.createEvent(TimelineEventDraft(title = "Race", lane = "Dodo"))
		val component = opened(event.id)

		assertEquals(listOf("Alice", "Malice"), component.suggestLanes("ali"))
		assertEquals(listOf("Malice"), component.suggestLanes("alice"))
	}

	// endregion

	// region Subject refresh

	@Test
	fun `moving the placed scene out of its chapter refreshes the open view`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeScene(4, null, null, lane = null, milestone = false).event()
		val component = opened(placed.id)
		assertEquals("Chapter ID 2", assertIs<SubjectSummary.Scene>(component.state.value.subject).chapterPath)

		sceneRepository.moveScene(
			MoveRequest(
				id = 4,
				toPosition = InsertPosition(NodeCoordinates(globalIndex = 1, parentIndex = 0, childLocalIndex = 0), before = false),
			)
		)
		advanceUntilIdle()

		assertEquals("", assertIs<SubjectSummary.Scene>(component.state.value.subject).chapterPath)
	}

	@Test
	fun `archiving the placed scene shows it archived`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeScene(4, null, null, lane = null, milestone = false).event()
		val component = opened(placed.id)

		sceneRepository.archiveScene(sceneRepository.getSceneItemFromId(4)!!)
		advanceUntilIdle()

		assertEquals(4, assertIs<SubjectSummary.Archived>(component.state.value.subject).scene.id)
		assertEquals("Scene ID 4", component.state.value.displayTitle)
	}

	@Test
	fun `deleting the placed scene shows it missing`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeScene(4, null, null, lane = null, milestone = false).event()
		val component = opened(placed.id)

		sceneRepository.deleteScene(sceneRepository.getSceneItemFromId(4)!!)
		advanceUntilIdle()

		assertEquals(SubjectSummary.Missing, component.state.value.subject)
		assertEquals("Scene ID 4", component.state.value.displayTitle)
	}

	@Test
	fun `deleting the placed entry shows it missing`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeEntry(TEA_PARTY, null, null, lane = null, milestone = false).event()
		val component = opened(placed.id)

		encyclopediaRepository.deleteEntry(encyclopediaRepository.findEntryDef(TEA_PARTY)!!)
		advanceUntilIdle()

		assertEquals(SubjectSummary.Missing, component.state.value.subject)
		assertEquals("Tea Party", component.state.value.displayTitle)
	}

	@Test
	fun `changes to other scenes and entries leave the subject alone`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeScene(4, null, null, lane = null, milestone = false).event()
		val component = opened(placed.id)
		val before = component.state.value.subject

		sceneRepository.renameScene(sceneRepository.getSceneItemFromId(6)!!, "Elsewhere")
		sceneMetadataRepository.storeMetadata(SceneMetadata(confirmedReferences = setOf(ALICE)), sceneId = 5)
		val alice = encyclopediaRepository.findEntryDef(ALICE)!!
		encyclopediaRepository.updateEntry(
			alice,
			name = alice.name,
			text = "Older now.",
			tags = emptySet(),
			excludeFromDictionary = false,
			images = emptyList(),
		)
		advanceUntilIdle()

		assertTrue(before === component.state.value.subject)
	}

	// endregion
}
