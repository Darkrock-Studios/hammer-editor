package components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.EventPreview
import com.darkrockstudios.apps.hammer.common.components.timeline.LaneCount
import com.arkivanov.decompose.value.MutableValue
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverviewComponent
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSelection
import com.darkrockstudios.apps.hammer.common.components.timeline.isDrawn
import com.darkrockstudios.apps.hammer.common.components.timeline.toLayoutInput
import com.darkrockstudios.apps.hammer.common.data.sceneeditorrepository.scenemetadata.SceneMetadata
import com.darkrockstudios.apps.hammer.common.data.MenuDescriptor
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineDefResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineSummary
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.timeline.layout.MonospaceEstimateMeasurer
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineLayoutEngine
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TimeLineOverviewComponentTest : TimelineComponentTestBase() {


	private val selection = MutableValue<TimelineSelection>(TimelineSelection.Main)
	private val addedMenus = mutableListOf<MenuDescriptor>()
	private val removedMenus = mutableListOf<String>()
	private var newTimelineRequests = 0

	private fun newComponent() = TimeLineOverviewComponent(
		componentContext = context,
		projectDef = projectDef,
		selection = selection,
		addMenu = { addedMenus += it },
		removeMenu = { removedMenus += it },
		onRequestNewTimeline = { newTimelineRequests++ },
	)

	private suspend fun newTimeline(name: String, mode: TimelineMode = TimelineMode.MANUAL): TimelineDef =
		(timelineService.createTimeline(name, mode, CalendarDef().takeIf { mode == TimelineMode.DATED }) as TimelineDefResult.Ok).def

	private fun TestScope.previewOf(component: TimeLineOverviewComponent, eventId: Int): EventPreview? {
		component.requestPreview(eventId)
		advanceUntilIdle()
		return component.previews.value[eventId]
	}

	@Test
	fun `rows are empty until the timeline loads, then follow it`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		assertNull(component.state.value.rows)

		context.resume()
		advanceUntilIdle()
		assertEquals(emptyList(), component.state.value.rows)

		timelineService.createEvent(TimelineEventDraft(content = "Rain falls"))
		advanceUntilIdle()

		assertEquals(listOf("Rain falls"), component.state.value.rows?.map { it.title })
	}

	@Test
	fun `rows resolve titles, subtitles and lane indices`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.placeScene(4, null, null, lane = "Alice", milestone = true)
		timelineService.placeEntry(TEA_PARTY, null, null, lane = "Hatter", milestone = false)
		timelineService.createEvent(TimelineEventDraft(title = "Storm", label = "Night", lane = "Alice"))
		timelineService.createEvent(TimelineEventDraft(content = "Rain falls"))

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val rows = component.state.value.rows!!
		assertEquals(listOf("Scene ID 4", "Tea Party", "Storm", "Rain falls"), rows.map { it.title })
		assertEquals(listOf(TimelineKind.SCENE, TimelineKind.ENTRY, TimelineKind.EVENT, TimelineKind.EVENT), rows.map { it.event.kind })
		assertEquals(listOf("Chapter ID 2", null, null, null), rows.map { it.subtitle })
		assertEquals(listOf(0, 1, 0, null), rows.map { it.laneIndex })
		assertEquals(listOf("", "", "Night", ""), rows.map { it.whenText })
		assertTrue(rows.none { it.subjectMissing })
		assertEquals(listOf(LaneCount("Alice", 2), LaneCount("Hatter", 1)), component.state.value.lanes)
		assertEquals(TimelineMode.MANUAL, component.state.value.mode)
	}

	@Test
	fun `lane indices follow the lane order before the alphabet`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.updateDefinition { it.copy(laneOrder = listOf("Queen")) }
		timelineService.createEvent(TimelineEventDraft(title = "Croquet", lane = "Queen"))
		timelineService.createEvent(TimelineEventDraft(title = "Fall", lane = "Alice"))

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(listOf("Queen", "Alice"), component.state.value.lanes.map { it.lane })
		assertEquals(listOf(0, 1), component.state.value.rows!!.map { it.laneIndex })
	}

	@Test
	fun `a placement whose subject is gone keeps its cached title`() = runTest(mainTestDispatcher) {
		givenProject()
		timeLineRepository.createEvent(
			TimelineEventDraft(kind = TimelineKind.SCENE, subject = 999, cachedTitle = "Gone scene")
		)

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val row = component.state.value.rows!!.single()
		assertTrue(row.subjectMissing)
		assertEquals("Gone scene", row.title)
	}

	@Test
	fun `a placed scene that is archived is flagged, not drawn, and returns when unarchived`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(CalendarDef())
		timelineService.placeScene(4, StoryDate(mapOf("year" to 3L)), null, lane = null, milestone = false)
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.setFrame(TimelineFrame.Story)

		sceneRepository.archiveScene(sceneRepository.getSceneItemFromId(4)!!)
		advanceUntilIdle()

		val archived = component.state.value.rows!!.single()
		assertTrue(archived.subjectArchived)
		assertFalse(archived.subjectMissing)
		assertEquals("Scene ID 4", archived.title)
		assertFalse(component.state.value.storyFrameAvailable)
		assertEquals(TimelineFrame.Everything, component.state.value.frame)

		sceneRepository.unarchiveScene(sceneRepository.getArchivedSceneFromId(4)!!)
		advanceUntilIdle()

		assertFalse(component.state.value.rows!!.single().subjectArchived)
		assertTrue(component.state.value.storyFrameAvailable)
	}

	@Test
	fun `toggling a lane filters by it and toggling again clears it`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "Croquet", lane = "Queen"))
		timelineService.createEvent(TimelineEventDraft(title = "Fall", lane = "Alice"))

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.toggleLane("Queen")
		assertEquals(setOf("Queen"), component.state.value.laneFilter)
		component.toggleLane("Alice")
		assertEquals(setOf("Queen", "Alice"), component.state.value.laneFilter)
		component.toggleLane("Queen")
		assertEquals(setOf("Alice"), component.state.value.laneFilter)
		component.clearFilters()
		assertEquals(emptySet(), component.state.value.laneFilter)
	}

	@Test
	fun `a lane that leaves the timeline leaves the filter`() = runTest(mainTestDispatcher) {
		givenProject()
		val croquet = (timelineService.createEvent(TimelineEventDraft(title = "Croquet", lane = "Queen")) as TimelineResult.Ok).event!!

		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.toggleLane("Queen")

		timelineService.setLane(croquet.id, null)
		advanceUntilIdle()

		assertEquals(emptySet(), component.state.value.laneFilter)
	}

	private fun TimeLineOverviewComponent.listed(): List<String> = state.value.filtered.listRows.map { it.title }

	@Test
	fun `on a manual timeline the search narrows the list to what it matches`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "Squall", content = "A well\\-known storm"))
		timelineService.createEvent(TimelineEventDraft(title = "Croquet", label = "Storm season"))
		timelineService.placeEntry(TEA_PARTY, null, null, lane = null, milestone = false)
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertEquals(listOf("Squall", "Croquet", "Tea Party"), component.listed())
		assertTrue(component.state.value.filtered.reorderable)

		component.setSearchQuery("  storm ")
		assertEquals(listOf("Squall", "Croquet"), component.listed(), "on the body or the label, trimmed")
		assertFalse(component.state.value.filtered.reorderable)
		assertEquals("  storm ", component.state.value.searchQuery)

		component.setSearchQuery("well-known")
		assertEquals(listOf("Squall"), component.listed(), "across a stored escape")

		component.setSearchQuery("tea party")
		assertEquals(listOf("Tea Party"), component.listed(), "on a placement's subject name")

		component.setSearchQuery(" ")
		assertEquals(listOf("Squall", "Croquet", "Tea Party"), component.listed())
		assertTrue(component.state.value.filtered.reorderable)
		assertEquals(emptyList(), component.state.value.filtered.searchMatches, "only a dated drawing rings matches")
		assertEquals(emptyList(), component.state.value.filtered.drawingRows)
	}

	@Test
	fun `a tag filter lists only rows with every chosen tag`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "Fall", tags = setOf("act-one", "storm")))
		timelineService.createEvent(TimelineEventDraft(title = "Tea", tags = setOf("act-one")))
		timelineService.createEvent(TimelineEventDraft(title = "Trial"))
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.toggleTag("act-one")
		assertEquals(listOf("Fall", "Tea"), component.listed())
		assertFalse(component.state.value.filtered.reorderable)

		component.toggleTag("storm")
		assertEquals(listOf("Fall"), component.listed())

		component.toggleTag("act-one")
		assertEquals(setOf("storm"), component.state.value.tagFilter)
		assertEquals(listOf("Fall"), component.listed())

		component.clearFilters()
		assertEquals(listOf("Fall", "Tea", "Trial"), component.listed())
		assertTrue(component.state.value.filtered.reorderable)
	}

	@Test
	fun `a lane filter lists only the events in its lanes`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "Croquet", lane = "Queen"))
		timelineService.createEvent(TimelineEventDraft(title = "Fall", lane = "Alice"))
		timelineService.createEvent(TimelineEventDraft(title = "Rain"))
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.toggleLane("Queen")
		assertEquals(listOf("Croquet"), component.listed())
		assertFalse(component.state.value.filtered.reorderable)

		component.toggleLane("Alice")
		assertEquals(listOf("Croquet", "Fall"), component.listed())
	}

	@Test
	fun `the search, tags and lanes narrow the list together, and clearing the filters keeps the search`() =
		runTest(mainTestDispatcher) {
			givenProject()
			timelineService.createEvent(TimelineEventDraft(title = "Storm", lane = "Alice", tags = setOf("act-one")))
			timelineService.createEvent(TimelineEventDraft(title = "Storm at court", lane = "Queen", tags = setOf("act-one")))
			timelineService.createEvent(TimelineEventDraft(title = "Storm again", lane = "Alice"))
			timelineService.createEvent(TimelineEventDraft(title = "Calm", lane = "Alice", tags = setOf("act-one")))
			val component = newComponent()
			context.resume()
			advanceUntilIdle()

			component.setSearchQuery("storm")
			component.toggleTag("act-one")
			component.toggleLane("Alice")
			assertEquals(listOf("Storm"), component.listed())

			component.clearFilters()
			assertEquals("storm", component.state.value.searchQuery)
			assertEquals(listOf("Storm", "Storm at court", "Storm again"), component.listed())
		}

	@Test
	fun `on a dated timeline the search rings drawn matches in the lane filter without narrowing anything`() =
		runTest(mainTestDispatcher) {
			givenProject()
			timelineService.switchToDated(CalendarDef())
			fun year(y: Long) = StoryDate(mapOf("year" to y))
			suspend fun create(title: String, start: Int?, lane: String? = null, tags: Set<String> = setOf("act-one")) =
				timelineService.createEvent(TimelineEventDraft(title = title, lane = lane, tags = tags, start = start?.let { year(it.toLong()) })).event()
			val sea = create("Storm at sea", 3, lane = "Alice")
			create("Croquet", 2, lane = "Queen")
			val teacup = create("Storm in a teacup", 1, lane = "Queen")
			val unlaned = create("Storm unlaned", 4)
			val untagged = create("Storm untagged", 5, tags = emptySet())
			timeLineRepository.createEvent(TimelineEventDraft(title = "Storm undated", tags = setOf("act-one")))
			val component = newComponent()
			context.resume()
			advanceUntilIdle()
			fun filtered() = component.state.value.filtered

			component.setSearchQuery("storm")
			assertEquals(listOf(teacup.id, sea.id, unlaned.id, untagged.id), filtered().searchMatches, "drawn, in display order")
			assertEquals(6, filtered().drawingRows.size, "the drawing keeps every event")
			assertEquals(6, filtered().listRows.size)
			assertFalse(filtered().reorderable)

			component.toggleTag("act-one")
			assertEquals(listOf(teacup.id, sea.id, unlaned.id), filtered().searchMatches)
			assertEquals(
				listOf("Storm in a teacup", "Croquet", "Storm at sea", "Storm unlaned", "Storm undated"),
				filtered().drawingRows.map { it.title },
			)

			component.toggleLane("Alice")
			assertEquals(listOf(sea.id, unlaned.id), filtered().searchMatches, "unlaned events stay in the drawing's ledger")
			assertEquals(5, filtered().drawingRows.size, "the drawing filters lanes itself")
			assertEquals(listOf("Storm at sea"), filtered().listRows.map { it.title })
		}

	@Test
	fun `the search and filters survive recreation`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "Storm", lane = "Alice", tags = setOf("act-one", "weather")))
		timelineService.createEvent(TimelineEventDraft(title = "Storm at court", lane = "Queen", tags = setOf("act-one")))
		timelineService.createEvent(TimelineEventDraft(title = "Calm", lane = "Alice", tags = setOf("act-one", "weather")))
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.setSearchQuery("storm")
		component.toggleTag("weather")
		component.toggleTag("act-one")
		component.toggleLane("Alice")

		val recreated = context.saveAndRecreate()
		val restored = TimeLineOverviewComponent(recreated, projectDef, selection, {}, {}, {})
		recreated.resume()
		advanceUntilIdle()

		assertEquals("storm", restored.state.value.searchQuery)
		assertEquals(listOf("weather", "act-one"), restored.state.value.tagFilter.toList(), "in the order chosen")
		assertEquals(setOf("Alice"), restored.state.value.laneFilter)
		assertEquals(listOf("Storm"), restored.listed())
	}

	@Test
	fun `a kept tag that no event carries any more leaves the filter`() = runTest(mainTestDispatcher) {
		givenProject()
		val fall = timelineService.createEvent(TimelineEventDraft(title = "Fall", tags = setOf("storm", "act-one"))).event()
		timelineService.createEvent(TimelineEventDraft(title = "Tea", tags = setOf("act-one")))
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.toggleTag("storm")
		component.toggleTag("act-one")

		val recreated = context.saveAndRecreate()
		timelineService.updateEvent(fall.copy(tags = setOf("act-one")))
		val restored = TimeLineOverviewComponent(recreated, projectDef, selection, {}, {}, {})
		recreated.resume()
		advanceUntilIdle()

		assertEquals(setOf("act-one"), restored.state.value.tagFilter)
		assertEquals(listOf("Fall", "Tea"), restored.listed())
	}

	@Test
	fun `moving is refused on a dated timeline`() = runTest(mainTestDispatcher) {
		givenProject()
		val first = (timelineService.createEvent(TimelineEventDraft(title = "One")) as TimelineResult.Ok).event!!
		timelineService.createEvent(TimelineEventDraft(title = "Two"))

		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertTrue(component.moveEvent(first, 1, after = true))

		timelineService.switchToDated(CalendarDef())
		advanceUntilIdle()

		assertEquals(TimelineMode.DATED, component.state.value.mode)
		assertFalse(component.moveEvent(first, 0, after = false))
		assertTrue(component.state.value.rows!!.all { it.start != null }, "Dated rows carry their start")
	}

	@Test
	fun `dated rows read their dates and undated legacy events are listed`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "One", label = "Dawn"))
		timelineService.createEvent(TimelineEventDraft(title = "Two"))
		timelineService.switchToDated(CalendarDef(epochName = "Landing"))
		val legacy = timeLineRepository.createEvent(TimelineEventDraft(title = "Legacy"))

		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val state = component.state.value
		assertEquals(CalendarDef(epochName = "Landing"), state.calendar)
		assertEquals(
			listOf(StoryDate(mapOf("year" to 0L)), StoryDate(mapOf("year" to 1L)), null),
			state.rows!!.map { it.start },
		)
		assertEquals(listOf(CalendarDef(epochName = "Landing"), CalendarDef(epochName = "Landing"), null), state.rows.map { it.calendar })
		assertEquals(listOf("Dawn", "", ""), state.rows.map { it.whenText }, "The label stays as the fallback")
		assertEquals(listOf(legacy.id), state.undatedIds)
	}

	@Test
	fun `the drawing starts in sequence and follows the chosen view`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertEquals(TimelineView.SEQUENCE, component.state.value.view)

		component.setView(TimelineView.TO_SCALE)

		assertEquals(TimelineView.TO_SCALE, component.state.value.view)
	}

	private fun TimelineResult.event(): TimeLineEvent = (this as TimelineResult.Ok).event!!

	@Test
	fun `a span or a coarse date can be framed, a minute cannot, nor the band already framed`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(CalendarDef())
		val siege = timelineService.createEvent(
			TimelineEventDraft(title = "Siege", lane = "Keep", start = StoryDate(mapOf("year" to 1L)), end = StoryDate(mapOf("year" to 4L)))
		).event()
		val winter = timelineService.createEvent(
			TimelineEventDraft(title = "Winter", start = StoryDate(mapOf("year" to 2L)))
		).event()
		val bell = timelineService.createEvent(
			TimelineEventDraft(
				title = "Bell",
				start = StoryDate(mapOf("year" to 2L, "day" to 3L, "hour" to 4L, "minute" to 5L)),
			)
		).event()
		val instant = StoryDate(mapOf("year" to 2L, "day" to 3L, "hour" to 4L, "minute" to 5L))
		val moment = timelineService.createEvent(TimelineEventDraft(title = "Moment", start = instant, end = instant)).event()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertTrue(component.canFrame(siege.id))
		assertTrue(component.canFrame(winter.id))
		assertFalse(component.canFrame(bell.id))
		assertFalse(component.canFrame(moment.id), "a range that starts and ends on the same minute draws no span")
		assertFalse(component.canFrame(999), "no such event")

		component.setFrame(TimelineFrame.Band(siege.id))

		assertFalse(component.canFrame(siege.id))
	}

	@Test
	fun `an event can be framed exactly when the drawing draws it as a span`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(CalendarDef())
		val instant = StoryDate(mapOf("year" to 2L, "day" to 3L, "hour" to 4L, "minute" to 5L))
		val later = StoryDate(mapOf("year" to 2L, "day" to 3L, "hour" to 6L, "minute" to 0L))
		val year2 = StoryDate(mapOf("year" to 2L))
		val drafts = listOf(
			TimelineEventDraft(title = "Siege", lane = "Keep", start = StoryDate(mapOf("year" to 1L)), end = StoryDate(mapOf("year" to 4L))),
			TimelineEventDraft(title = "Watch", lane = "Keep", start = instant, end = later),
			TimelineEventDraft(title = "March", start = instant, end = later),
			TimelineEventDraft(title = "Winter", lane = "Keep", start = year2),
			TimelineEventDraft(title = "Thaw", start = year2),
			TimelineEventDraft(title = "Season", lane = "Keep", start = year2, end = year2),
			TimelineEventDraft(title = "Bell", lane = "Keep", start = instant),
			TimelineEventDraft(title = "Chime", start = instant),
			TimelineEventDraft(title = "Moment", lane = "Keep", start = instant, end = instant),
			TimelineEventDraft(title = "Blink", start = instant, end = instant),
			TimelineEventDraft(title = "Exile", lane = "Keep", start = StoryDate(mapOf("year" to 10L)), end = StoryDate(mapOf("year" to 12L))),
			TimelineEventDraft(title = "Wander", start = StoryDate(mapOf("year" to 10L)), end = StoryDate(mapOf("year" to 12L))),
		)
		val ids = drafts.associate { draft -> draft.title!! to timelineService.createEvent(draft).event().id } +
			("Scene" to timelineService.placeScene(4, year2, null, lane = null, milestone = false).event().id)
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		fun drawnAsSpans(): Set<String> {
			val state = component.state.value
			val input = toLayoutInput(
				rows = state.filtered.drawingRows,
				lanes = state.lanes,
				laneFilter = state.laneFilter,
				calendar = state.calendar!!,
				view = TimelineView.TO_SCALE,
				widthDp = 840f,
				phone = false,
				frame = state.frame,
			)
			val layout = TimelineLayoutEngine.layout(input, MonospaceEstimateMeasurer())
			return ids.filterValues { id ->
				layout.bands.any { it.eventId == id } ||
					layout.bars.any { it.eventId == id } ||
					layout.ledgerRows.any { it.eventId == id && it.anchorY1Dp != null }
			}.keys
		}

		val spans = setOf("Siege", "Watch", "March", "Winter", "Thaw", "Season", "Scene")
		val framable = ids.filterValues { component.canFrame(it) }.keys
		assertEquals(spans + setOf("Exile", "Wander"), framable)
		assertEquals(framable, drawnAsSpans())

		component.setFrame(TimelineFrame.Story)

		val framableInStory = ids.filterValues { component.canFrame(it) }.keys
		assertEquals(spans, framableInStory, "the story frame clips out the spans past the scenes")
		assertEquals(framableInStory, drawnAsSpans())
	}

	@Test
	fun `an event the filters keep out of the drawing cannot be framed`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(CalendarDef())
		val years = { from: Long, to: Long -> StoryDate(mapOf("year" to from)) to StoryDate(mapOf("year" to to)) }
		val (siegeStart, siegeEnd) = years(1L, 4L)
		val siege = timelineService.createEvent(
			TimelineEventDraft(title = "Siege", lane = "Keep", tags = setOf("war"), start = siegeStart, end = siegeEnd)
		).event()
		val (feastStart, feastEnd) = years(2L, 3L)
		timelineService.createEvent(
			TimelineEventDraft(title = "Feast", lane = "Hall", tags = setOf("peace"), start = feastStart, end = feastEnd)
		)
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertTrue(component.canFrame(siege.id))

		component.toggleLane("Hall")
		assertFalse(component.canFrame(siege.id), "the lane filter hides it")

		component.clearFilters()
		component.toggleTag("peace")
		assertFalse(component.canFrame(siege.id), "the tag filter hides it")
	}

	@Test
	fun `framing a band survives recreation and back returns to everything`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm", lane = "Keep")).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertEquals(TimelineFrame.Everything, component.state.value.frame)
		assertFalse(context.back(), "Nothing to go back from while everything is shown")

		component.setFrame(TimelineFrame.Band(storm.id))
		assertEquals(TimelineFrame.Band(storm.id), component.state.value.frame)

		val restored = TimeLineOverviewComponent(context.saveAndRecreate(), projectDef, selection, {}, {}, {})
		assertEquals(TimelineFrame.Band(storm.id), restored.state.value.frame)

		assertTrue(context.back())
		assertEquals(TimelineFrame.Everything, component.state.value.frame)
		assertFalse(context.back())
	}

	@Test
	fun `a framed band that leaves the timeline returns to everything`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm")).event()
		timelineService.createEvent(TimelineEventDraft(title = "Rain"))
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.setFrame(TimelineFrame.Band(storm.id))

		timeLineRepository.deleteEvent(storm)
		advanceUntilIdle()

		assertEquals(TimelineFrame.Everything, component.state.value.frame)
	}

	@Test
	fun `the story frame needs a placed scene`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "Storm"))
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertFalse(component.state.value.storyFrameAvailable)
		component.setFrame(TimelineFrame.Story)
		assertEquals(TimelineFrame.Everything, component.state.value.frame)

		timelineService.placeScene(4, StoryDate(mapOf("year" to 3L)), null, lane = null, milestone = false)
		advanceUntilIdle()

		assertTrue(component.state.value.storyFrameAvailable)
		component.setFrame(TimelineFrame.Story)
		assertEquals(TimelineFrame.Story, component.state.value.frame)
	}

	@Test
	fun `the summary of a placed scene lists its cast, people first`() = runTest(mainTestDispatcher) {
		givenProject()
		sceneMetadataRepository.storeMetadata(SceneMetadata(confirmedReferences = setOf(TEA_PARTY, WONDERLAND, ALICE)), sceneId = 4)
		val placed = timelineService.placeScene(4, null, null, lane = "Alice", milestone = false).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val summary = previewOf(component, placed.id)!!
		assertEquals(placed.id, summary.eventId)
		assertEquals("Scene ID 4", summary.title)
		assertEquals(listOf(ALICE, WONDERLAND, TEA_PARTY), summary.references.map { it.id })
		assertNull(summary.imageId)
	}

	@Test
	fun `the summary of a plain event orders its references people, places, then scenes`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(
			TimelineEventDraft(title = "Storm", references = linkedSetOf(4, WONDERLAND, ALICE))
		).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(listOf(ALICE, WONDERLAND, 4), previewOf(component, storm.id)!!.references.map { it.id })
	}

	@Test
	fun `the summary of a placed entry shows its opening text`() = runTest(mainTestDispatcher) {
		givenProject()
		val placed = timelineService.placeEntry(TEA_PARTY, null, null, lane = null, milestone = false).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val summary = previewOf(component, placed.id)!!
		assertEquals("The Hatter pours tea.", summary.excerpt)
		assertEquals(emptyList(), summary.references)
	}

	@Test
	fun `a change elsewhere on the timeline keeps an event's cached summary`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm", references = linkedSetOf(ALICE))).event()
		val rain = timelineService.createEvent(TimelineEventDraft(title = "Rain")).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		val summary = assertNotNull(previewOf(component, storm.id))

		timelineService.updateEvent(timeLineRepository.getTimelineEvent(rain.id)!!.copy(title = "Hail"))
		advanceUntilIdle()

		assertEquals("Hail", component.state.value.rows!!.first { it.event.id == rain.id }.title)
		assertSame(summary, previewOf(component, storm.id))
	}

	@Test
	fun `a change to an event refreshes its summary`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm", references = linkedSetOf(ALICE))).event()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		previewOf(component, storm.id)

		timelineService.updateEvent(timeLineRepository.getTimelineEvent(storm.id)!!.copy(references = setOf(WONDERLAND, ALICE)))
		advanceUntilIdle()

		assertEquals(listOf(ALICE, WONDERLAND), previewOf(component, storm.id)!!.references.map { it.id })
	}

	@Test
	fun `a preview that cannot be read stays absent and the timeline keeps updating`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm", references = linkedSetOf(ALICE))).event()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		catalog.failure = IllegalStateException("catalog unreadable")

		assertNull(previewOf(component, storm.id))

		timelineService.createEvent(TimelineEventDraft(title = "Rain"))
		advanceUntilIdle()
		assertEquals(listOf("Storm", "Rain"), component.state.value.rows?.map { it.title })
		assertNull(component.previews.value[storm.id])

		catalog.failure = null
		assertEquals(listOf(ALICE), previewOf(component, storm.id)?.references?.map { it.id })
	}

	@Test
	fun `a plain event's preview has its content as plain text and its references`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(
			TimelineEventDraft(
				title = "Storm",
				content = "The **sky** splits and _rain_ falls.\n\nAlice runs for the keep.",
				lane = "Keep",
				references = linkedSetOf(4, ALICE),
			)
		).event()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val preview = assertNotNull(previewOf(component, storm.id))

		assertEquals("Storm", preview.title)
		assertEquals("Keep", preview.lane)
		assertEquals("The sky splits and rain falls. Alice runs for the keep.", preview.excerpt)
		assertEquals(listOf(ALICE, 4), preview.references.map { it.id })
		assertNull(preview.imageId)
		assertEquals(EventPreview.SubjectState.Present, preview.subjectState)
	}

	@Test
	fun `an untitled event's preview does not repeat its first line`() = runTest(mainTestDispatcher) {
		givenProject()
		val rain = timelineService.createEvent(TimelineEventDraft(content = "Rain falls\n\nAll night long.")).event()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val preview = assertNotNull(previewOf(component, rain.id))

		assertEquals("Rain falls", preview.title)
		assertEquals("All night long.", preview.excerpt)
	}

	@Test
	fun `a placed entry's preview carries its primary image and opening text`() = runTest(mainTestDispatcher) {
		givenProject()
		encyclopediaRepository.addEntryImage(encyclopediaRepository.getEntryDef(TEA_PARTY), 77)
		val placed = timelineService.placeEntry(TEA_PARTY, null, null, lane = null, milestone = false).event()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		val preview = assertNotNull(previewOf(component, placed.id))

		assertEquals(77, preview.imageId)
		assertEquals("The Hatter pours tea.", preview.excerpt)
		assertEquals(emptyList(), preview.references)
	}

	@Test
	fun `a preview is cached until its event changes`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm", content = "Thunder.")).event()
		val rain = timelineService.createEvent(TimelineEventDraft(title = "Rain")).event()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		val first = assertNotNull(previewOf(component, storm.id))

		timelineService.updateEvent(timeLineRepository.getTimelineEvent(rain.id)!!.copy(title = "Hail"))
		advanceUntilIdle()
		assertSame(first, previewOf(component, storm.id), "A change to another event keeps the preview")

		timelineService.updateEvent(timeLineRepository.getTimelineEvent(storm.id)!!.copy(content = "Lightning."))
		advanceUntilIdle()
		assertNull(component.previews.value[storm.id], "The changed event's preview leaves until it is asked for again")
		assertEquals("Lightning.", previewOf(component, storm.id)?.excerpt)
	}

	@Test
	fun `nothing is read until a preview is asked for`() = runTest(mainTestDispatcher) {
		givenProject()
		val storm = timelineService.createEvent(TimelineEventDraft(title = "Storm", content = "Thunder.")).event()
		val rain = timelineService.createEvent(TimelineEventDraft(title = "Rain", content = "Drizzle.")).event()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertEquals(emptyMap(), component.previews.value)

		component.requestPreview(storm.id)
		component.requestPreview(rain.id)
		advanceUntilIdle()

		assertEquals(mapOf(storm.id to "Thunder.", rain.id to "Drizzle."), component.previews.value.mapValues { it.value.excerpt })
	}

	@Test
	fun `there is no preview for an event the timeline does not have`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertNull(previewOf(component, 999))
	}

	// region Timelines

	@Test
	fun `rows, tags and lanes come from the selected timeline only`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		timelineService.updateDefinition(before.id) { it.copy(laneOrder = listOf("Hayes")) }
		timelineService.createEvent(TimelineEventDraft(title = "Dinner", lane = "Ward", tags = setOf("dinner")))
		timelineService.createEvent(TimelineEventDraft(title = "Toast", lane = "Ward", tags = setOf("dinner", "wine")))
		timelineService.createEvent(TimelineEventDraft(title = "Loan", lane = "Pike", tags = setOf("debt"), timeline = before.id))
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(listOf("Dinner", "Toast"), component.state.value.rows?.map { it.title })
		assertEquals(listOf(LaneCount("Ward", 2)), component.state.value.lanes)
		assertEquals(listOf(TagCount("dinner", 2), TagCount("wine", 1)), component.rankedTags.value)

		selection.value = TimelineSelection.Def(before.id)
		advanceUntilIdle()

		assertEquals(listOf("Loan"), component.state.value.rows?.map { it.title })
		assertEquals(listOf(LaneCount("Hayes", 0), LaneCount("Pike", 1)), component.state.value.lanes)
		assertEquals(listOf(TagCount("debt", 1)), component.rankedTags.value)
		assertEquals(TimelineSelection.Def(before.id), component.state.value.selection)
	}

	@Test
	fun `the timelines are listed with their counts, main first`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		timelineService.createEvent(TimelineEventDraft(title = "Dinner"))
		timelineService.createEvent(TimelineEventDraft(title = "Loan", timeline = before.id))
		timelineService.createEvent(TimelineEventDraft(title = "Note", timeline = before.id))
		timeLineRepository.createEvent(TimelineEventDraft(title = "Stray", timeline = "gone0001"))
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(
			listOf(TimelineSummary(TimelineDef.DEFAULT, 1), TimelineSummary(before, 2)),
			component.state.value.timelines,
		)
		assertEquals(1, component.state.value.orphanCount)
		assertEquals(TimelineDef.DEFAULT, component.state.value.selectedTimeline?.def)
	}

	@Test
	fun `a switch resets the frame and keeps the drawing and the search, and filters prune themselves`() =
		runTest(mainTestDispatcher) {
			givenProject()
			timelineService.switchToDated(CalendarDef())
			val before = newTimeline("Before", TimelineMode.DATED)
			val year = { y: Long -> StoryDate(mapOf("year" to y)) }
			val siege = timelineService.createEvent(
				TimelineEventDraft(title = "Siege", lane = "Keep", tags = setOf("war"), start = year(1), end = year(4))
			).event()
			timelineService.createEvent(TimelineEventDraft(title = "Loan", lane = "Bank", tags = setOf("debt"), start = year(2), timeline = before.id))
			val component = newComponent()
			context.resume()
			advanceUntilIdle()
			component.setView(TimelineView.TO_SCALE)
			component.setSearchQuery("siege")
			component.toggleTag("war")
			component.toggleLane("Keep")
			component.setFrame(TimelineFrame.Band(siege.id))

			selection.value = TimelineSelection.Def(before.id)
			advanceUntilIdle()

			val state = component.state.value
			assertEquals(TimelineFrame.Everything, state.frame)
			assertEquals(TimelineView.TO_SCALE, state.view)
			assertEquals("siege", state.searchQuery)
			assertEquals(emptySet(), state.tagFilter)
			assertEquals(emptySet(), state.laneFilter)
			assertEquals(listOf("Loan"), state.rows?.map { it.title })
		}

	@Test
	fun `the deleted-timeline bucket lists stragglers read-only`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(CalendarDef())
		timeLineRepository.createEvent(TimelineEventDraft(title = "Stray", lane = "Pike", timeline = "gone0001", start = StoryDate(mapOf("year" to 3L))))
		timeLineRepository.createEvent(TimelineEventDraft(title = "Lost", timeline = "gone0002"))
		val component = newComponent()
		context.resume()
		selection.value = TimelineSelection.Orphans
		advanceUntilIdle()

		val state = component.state.value
		assertTrue(state.isOrphans)
		assertNull(state.selectedTimeline)
		assertEquals(listOf("Stray", "Lost"), state.rows?.map { it.title })
		assertEquals(TimelineMode.MANUAL, state.mode)
		assertNull(state.calendar)
		assertTrue(state.rows!!.none { it.isDrawn })
		assertEquals(listOf(LaneCount("Pike", 1)), state.lanes)
		assertFalse(state.filtered.reorderable)
		assertFalse(component.moveEvent(state.rows!!.first().event, 1, after = true))
	}

	@Test
	fun `everything in the bucket moves to a timeline, undated where its date does not fit`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before", TimelineMode.DATED)
		timeLineRepository.createEvent(TimelineEventDraft(title = "Dated", timeline = "gone0001", start = StoryDate(mapOf("year" to 3L))))
		timeLineRepository.createEvent(TimelineEventDraft(title = "Undated", timeline = "gone0001"))
		val component = newComponent()
		context.resume()
		selection.value = TimelineSelection.Orphans
		advanceUntilIdle()

		assertEquals(2, component.moveOrphansTo(before.id))
		advanceUntilIdle()

		assertEquals(0, component.state.value.orphanCount)
		val moved = timeLineRepository.currentEvents().associate { it.title to it }
		assertEquals(before.id, moved.getValue("Dated").timeline)
		assertEquals(StoryDate(mapOf("year" to 3L)), moved.getValue("Dated").start)
		assertEquals(before.id, moved.getValue("Undated").timeline)
		assertNull(moved.getValue("Undated").start)
	}

	@Test
	fun `everything in the bucket can be deleted`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "Kept"))
		timeLineRepository.createEvent(TimelineEventDraft(title = "Stray", timeline = "gone0001"))
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(1, component.deleteOrphans())
		advanceUntilIdle()

		assertEquals(0, component.state.value.orphanCount)
		assertEquals(listOf("Kept"), timeLineRepository.currentEvents().map { it.title })
	}

	@Test
	fun `the desktop menu offers a new timeline while the overview is shown`() = runTest(mainTestDispatcher) {
		givenProject()
		newComponent()
		context.resume()
		advanceUntilIdle()

		val menu = addedMenus.single()
		val item = menu.items.single()
		item.action(item.id)
		assertEquals(1, newTimelineRequests)

		context.stop()
		assertEquals(listOf(menu.id), removedMenus)
	}

	// endregion
}
