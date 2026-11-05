package components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineKind
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.components.projectroot.CloseConfirm
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLine
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineComponent
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSelection
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSwitcher
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineDefResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import kotlinx.coroutines.flow.first
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

class TimeLineComponentTest : TimelineComponentTestBase() {

	private lateinit var event: TimeLineEvent

	private suspend fun TestScope.givenEvent() {
		givenProject()
		val created = timelineService.createEvent(
			TimelineEventDraft(label = "original date", content = "original content")
		)
		event = (created as TimelineResult.Ok).event!!
		advanceUntilIdle()
	}

	private val settingsShown = mutableListOf<String>()

	private fun newComponent(componentContext: TestComponentContext = context) = TimeLineComponent(
		componentContext = componentContext,
		projectDef = projectDef,
		updateShouldClose = {},
		addMenu = {},
		removeMenu = {},
		onShowGlobalSearchForTag = {},
		onShowScene = {},
		onShowEntry = {},
		onShowTimelineSettings = { settingsShown += it },
	)

	private suspend fun newTimeline(name: String, mode: TimelineMode = TimelineMode.MANUAL): String =
		(timelineService.createTimeline(name, mode, CalendarDef().takeIf { mode == TimelineMode.DATED }) as TimelineDefResult.Ok).def.id

	private suspend fun eventOn(timeline: String?, title: String, start: StoryDate? = null): TimeLineEvent =
		(timelineService.createEvent(TimelineEventDraft(title = title, timeline = timeline, start = start)) as TimelineResult.Ok).event!!

	private fun overview(component: TimeLineComponent) =
		(component.stack.value.items.first().instance as TimeLine.Destination.TimeLineOverviewDestination).component

	private fun viewEvent(component: TimeLineComponent) =
		(component.stack.value.active.instance as TimeLine.Destination.ViewEventDestination).component

	@Test
	fun `shouldConfirmClose is empty on the overview`() = runTest(mainTestDispatcher) {
		givenEvent()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
	}

	@Test
	fun `shouldConfirmClose is empty when viewing an event but not editing`() =
		runTest(mainTestDispatcher) {
			givenEvent()
			val component = newComponent()
			context.resume()
			component.showViewEvent(event.id)
			advanceUntilIdle()

			assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
		}

	@Test
	fun `shouldConfirmClose flags an in-progress timeline event edit`() =
		runTest(mainTestDispatcher) {
			givenEvent()
			val component = newComponent()
			context.resume()
			component.showViewEvent(event.id)
			advanceUntilIdle()

			val viewEvent = viewEvent(component)
			viewEvent.beginEdit()
			viewEvent.onEventTextChanged("a different body")
			advanceUntilIdle()

			assertEquals(setOf(CloseConfirm.Timeline), component.shouldConfirmClose())
		}

	@Test
	fun `discardUnsaved reverts a dirty timeline event edit`() = runTest(mainTestDispatcher) {
		givenEvent()
		val component = newComponent()
		context.resume()
		component.showViewEvent(event.id)
		advanceUntilIdle()

		val viewEvent = viewEvent(component)
		viewEvent.beginEdit()
		viewEvent.onEventTextChanged("a different body")
		advanceUntilIdle()

		component.discardUnsaved(CloseConfirm.Timeline)
		advanceUntilIdle()

		assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
		assertEquals(event.content, viewEvent.contentText.value)
	}

	@Test
	fun `discardUnsaved pops an open create event screen`() = runTest(mainTestDispatcher) {
		givenEvent()
		val component = newComponent()
		context.resume()
		component.showCreateEvent()
		advanceUntilIdle()
		assertEquals(setOf(CloseConfirm.Timeline), component.shouldConfirmClose())

		component.discardUnsaved(CloseConfirm.Timeline)
		advanceUntilIdle()

		assertIs<TimeLine.Destination.TimeLineOverviewDestination>(component.stack.value.active.instance)
		assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
	}

	@Test
	fun `an event opened over the drawing keeps the overview and back closes it`() = runTest(mainTestDispatcher) {
		givenEvent()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.showEventDialog(event.id)
		advanceUntilIdle()

		assertEquals(event.id, component.eventDialog.value.child?.instance?.eventId)
		assertIs<TimeLine.Destination.TimeLineOverviewDestination>(component.stack.value.active.instance)
		assertFalse(component.isAtRoot())

		component.onBack()
		advanceUntilIdle()

		assertNull(component.eventDialog.value.child)
		assertTrue(component.isAtRoot())
	}

	@Test
	fun `closing an event dialog with unsaved edits asks first`() = runTest(mainTestDispatcher) {
		givenEvent()
		val component = newComponent()
		context.resume()
		component.showEventDialog(event.id)
		advanceUntilIdle()
		val dialog = component.eventDialog.value.child!!.instance
		dialog.beginEdit()
		dialog.onEventTextChanged("a different body")
		advanceUntilIdle()

		assertEquals(setOf(CloseConfirm.Timeline), component.shouldConfirmClose())

		component.closeEventDialog()
		advanceUntilIdle()

		assertTrue(dialog.state.value.confirmClose)
		assertNotNull(component.eventDialog.value.child)

		component.discardUnsaved(CloseConfirm.Timeline)
		advanceUntilIdle()

		assertEquals(emptySet<CloseConfirm>(), component.shouldConfirmClose())
	}

	@Test
	fun `leaving the timeline from an event dialog closes the dialog`() = runTest(mainTestDispatcher) {
		givenEvent()
		val searched = mutableListOf<String>()
		val component = TimeLineComponent(
			componentContext = context,
			projectDef = projectDef,
			updateShouldClose = {},
			addMenu = {},
			removeMenu = {},
			onShowGlobalSearchForTag = { searched += it },
			onShowScene = {},
			onShowEntry = {},
			onShowTimelineSettings = {},
		)
		context.resume()
		component.showEventDialog(event.id)
		advanceUntilIdle()

		component.eventDialog.value.child!!.instance.showGlobalSearchForTag("storm")
		advanceUntilIdle()

		assertEquals(listOf("storm"), searched)
		assertNull(component.eventDialog.value.child)
	}

	@Test
	fun `framing from an event dialog frames the drawing on its band and closes the dialog`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(CalendarDef(), timeline = TimelineDef.MAIN_ID)
		val siege = (timelineService.createEvent(
			TimelineEventDraft(title = "Siege", start = StoryDate(mapOf("year" to 1L)), end = StoryDate(mapOf("year" to 4L)))
		) as TimelineResult.Ok).event!!
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.showEventDialog(siege.id)
		advanceUntilIdle()

		assertTrue(component.canFrameEvent(siege.id))
		component.frameEvent(siege.id)
		advanceUntilIdle()

		val overview = (component.stack.value.active.instance as TimeLine.Destination.TimeLineOverviewDestination).component
		assertEquals(TimelineFrame.Band(siege.id), overview.state.value.frame)
		assertNull(component.eventDialog.value.child)
		assertFalse(component.canFrameEvent(siege.id), "already framed")
	}

	// region Selection

	@Test
	fun `the selection starts on main and survives recreation`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertEquals(TimelineSelection.Main, component.selection.value)

		component.selectTimeline(TimelineSelection.Def(before))
		val recreated = context.saveAndRecreate()
		val restored = newComponent(recreated)
		recreated.resume()
		advanceUntilIdle()

		assertEquals(TimelineSelection.Def(before), restored.selection.value)
	}

	@Test
	fun `the deleted-timeline bucket is restored from saved state but never remembered`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		timeLineRepository.createEvent(TimelineEventDraft(title = "Stray", timeline = "gone0001"))
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.selectTimeline(TimelineSelection.Def(before))
		component.selectTimeline(TimelineSelection.Orphans)
		val recreated = context.saveAndRecreate()
		val restored = newComponent(recreated)
		recreated.resume()
		advanceUntilIdle()
		assertEquals(TimelineSelection.Orphans, restored.selection.value)

		val fresh = TestComponentContext.create()
		val reopened = newComponent(fresh)
		fresh.resume()
		advanceUntilIdle()
		assertEquals(TimelineSelection.Def(before), reopened.selection.value)
		fresh.destroy()
	}

	@Test
	fun `the last timeline shown is remembered for the project`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		component.selectTimeline(TimelineSelection.Def(before))

		val fresh = TestComponentContext.create()
		val reopened = newComponent(fresh)
		fresh.resume()
		advanceUntilIdle()

		assertEquals(TimelineSelection.Def(before), reopened.selection.value)
		assertEquals(before, selectionSetting.load(projectDef.id))
		fresh.destroy()
	}

	@Test
	fun `a remembered timeline that is gone falls back to main`() = runTest(mainTestDispatcher) {
		givenProject()
		selectionSetting.save(projectDef.id, "gone0001")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(TimelineSelection.Main, component.selection.value)
		assertEquals(TimelineDef.MAIN_ID, selectionSetting.load(projectDef.id))
	}

	@Test
	fun `a selected timeline deleted elsewhere falls back to main`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		val component = newComponent()
		context.resume()
		component.selectTimeline(TimelineSelection.Def(before))
		advanceUntilIdle()

		timelineService.deleteTimeline(before)
		advanceUntilIdle()

		assertEquals(TimelineSelection.Main, component.selection.value)
	}

	@Test
	fun `an emptied deleted-timeline bucket falls back to main`() = runTest(mainTestDispatcher) {
		givenProject()
		timeLineRepository.createEvent(TimelineEventDraft(title = "Stray", timeline = "gone0001"))
		val component = newComponent()
		context.resume()
		component.selectTimeline(TimelineSelection.Orphans)
		advanceUntilIdle()
		assertEquals(TimelineSelection.Orphans, component.selection.value)

		timelineService.deleteOrphans()
		advanceUntilIdle()

		assertEquals(TimelineSelection.Main, component.selection.value)
	}

	@Test
	fun `moving the bucket to a timeline shows that timeline without passing through main`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		timeLineRepository.createEvent(TimelineEventDraft(title = "Stray", timeline = "gone0001"))
		timeLineRepository.createEvent(TimelineEventDraft(title = "Lost", timeline = "gone0002"))
		val component = newComponent()
		context.resume()
		component.selectTimeline(TimelineSelection.Orphans)
		advanceUntilIdle()
		val seen = mutableListOf<TimelineSelection>()
		component.selection.subscribe { seen += it }

		assertEquals(2, overview(component).moveOrphansTo(before))
		advanceUntilIdle()

		assertEquals(TimelineSelection.Def(before), component.selection.value)
		assertFalse(TimelineSelection.Main in seen)
		assertEquals(listOf("Stray", "Lost"), overview(component).state.value.rows?.map { it.title })
	}

	@Test
	fun `a bucket move that moves nothing stays in the bucket`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		timeLineRepository.createEvent(TimelineEventDraft(kind = TimelineKind.SCENE, subject = 1, timeline = before))
		timeLineRepository.createEvent(TimelineEventDraft(kind = TimelineKind.SCENE, subject = 1, timeline = "gone0001"))
		val component = newComponent()
		context.resume()
		component.selectTimeline(TimelineSelection.Orphans)
		advanceUntilIdle()

		assertEquals(0, overview(component).moveOrphansTo(before))
		advanceUntilIdle()

		assertEquals(TimelineSelection.Orphans, component.selection.value)
		assertEquals(1, overview(component).state.value.orphanCount)
	}

	@Test
	fun `the overview follows the selection`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		eventOn(null, "On main")
		eventOn(before, "Before the dinner")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertEquals(listOf("On main"), overview(component).state.value.rows?.map { it.title })

		component.selectTimeline(TimelineSelection.Def(before))
		advanceUntilIdle()

		assertEquals(listOf("Before the dinner"), overview(component).state.value.rows?.map { it.title })
	}

	@Test
	fun `showing an event selects its timeline and opens it`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		val event = eventOn(before, "Before the dinner")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.showEvent(event.id)
		advanceUntilIdle()

		assertEquals(TimelineSelection.Def(before), component.selection.value)
		assertEquals(event.id, viewEvent(component).eventId)
	}

	@Test
	fun `showing a straggler opens it in the deleted-timeline bucket`() = runTest(mainTestDispatcher) {
		givenProject()
		val stray = timeLineRepository.createEvent(TimelineEventDraft(title = "Stray", timeline = "gone0001"))
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.showEvent(stray.id)
		advanceUntilIdle()

		assertEquals(TimelineSelection.Orphans, component.selection.value)
		assertEquals(stray.id, viewEvent(component).eventId)
		assertTrue(overview(component).state.value.isOrphans)
	}

	@Test
	fun `showing an event on another timeline swaps the open dialog for it`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		val onMain = (timelineService.placeScene(4, null, null, lane = null, milestone = false, timeline = TimelineDef.MAIN_ID) as TimelineResult.Ok).event!!
		val onBefore = (timelineService.placeScene(4, null, null, lane = null, milestone = false, timeline = before) as TimelineResult.Ok).event!!
		val component = newComponent()
		context.resume()
		component.showEventDialog(onMain.id)
		advanceUntilIdle()

		val dialog = component.eventDialog.value.child!!.instance
		dialog.showPlacement(dialog.state.value.alsoOn.single())
		advanceUntilIdle()

		assertEquals(TimelineSelection.Def(before), component.selection.value)
		assertEquals(onBefore.id, component.eventDialog.value.child?.instance?.eventId)
	}

	@Test
	fun `a new event is created on the selected timeline and none from the bucket`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		timeLineRepository.createEvent(TimelineEventDraft(title = "Stray", timeline = "gone0001"))
		val component = newComponent()
		context.resume()
		component.selectTimeline(TimelineSelection.Def(before))
		advanceUntilIdle()

		component.showCreateEvent()
		advanceUntilIdle()
		assertEquals(before, assertIs<TimeLine.Config.CreateEventConfig>(component.stack.value.active.configuration).timelineId)

		component.showOverview()
		component.selectTimeline(TimelineSelection.Orphans)
		component.showCreateEvent()
		advanceUntilIdle()
		assertIs<TimeLine.Config.TimeLineOverviewConfig>(component.stack.value.active.configuration)
	}

	@Test
	fun `an event on another timeline cannot be framed`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(CalendarDef(), timeline = TimelineDef.MAIN_ID)
		val before = newTimeline("Before", TimelineMode.DATED)
		val span = StoryDate(mapOf("year" to 1L))
		val elsewhere = (timelineService.createEvent(
			TimelineEventDraft(title = "Siege", timeline = before, start = span, end = StoryDate(mapOf("year" to 4L)))
		) as TimelineResult.Ok).event!!
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertFalse(component.canFrameEvent(elsewhere.id))
		component.frameEvent(elsewhere.id)
		assertEquals(TimelineFrame.Everything, overview(component).state.value.frame)
	}

	@Test
	fun `the settings link opens timeline settings on the selected timeline`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before the evening")
		val component = newComponent()
		context.resume()
		component.selectTimeline(TimelineSelection.Def(before))

		component.showTimelineSettings()

		assertEquals(listOf(before), settingsShown)
	}

	@Test
	fun `the settings link from the deleted-timeline bucket opens main`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		component.selectTimeline(TimelineSelection.Orphans)

		component.showTimelineSettings()

		assertEquals(listOf(TimelineDef.MAIN_ID), settingsShown)
	}

	// endregion

	// region Switcher

	@Test
	fun `creating a timeline selects it`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestCreateTimeline()
		assertEquals(TimelineSwitcher.Dialog.Create(), component.switcher.value.dialog)
		component.createTimeline("Before", TimelineMode.DATED, CalendarPreset.GREGORIAN)
		advanceUntilIdle()

		val created = timelineService.timelines.first().single { it.name == "Before" }
		assertEquals(TimelineMode.DATED, created.mode)
		assertEquals(CalendarDef(CalendarPreset.GREGORIAN), created.calendar)
		assertEquals(TimelineSelection.Def(created.id), component.selection.value)
		assertNull(component.switcher.value.dialog)
	}

	@Test
	fun `a refused name keeps the dialog open with its draft and the error`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestCreateTimeline()
		component.createTimeline("main", TimelineMode.MANUAL, CalendarPreset.STORY)
		advanceUntilIdle()

		assertEquals(TimelineSwitcher.Dialog.Create(name = "main"), component.switcher.value.dialog)
		assertEquals(TimeLineEventError.TIMELINE_NAME_INVALID, component.switcher.value.error)
		assertEquals(TimelineSelection.Main, component.selection.value)
	}

	@Test
	fun `renaming a timeline writes the new name`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestRenameTimeline(before)
		advanceUntilIdle()
		assertEquals(TimelineSwitcher.Dialog.Rename(before, "Before"), component.switcher.value.dialog)
		component.renameTimeline("Before the evening")
		advanceUntilIdle()

		assertEquals("Before the evening", timelineService.definition(before)?.name)
		assertNull(component.switcher.value.dialog)
	}

	@Test
	fun `deleting the selected timeline counts its events, then returns to main`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		eventOn(before, "One")
		eventOn(before, "Two")
		val component = newComponent()
		context.resume()
		component.selectTimeline(TimelineSelection.Def(before))
		advanceUntilIdle()

		component.requestDeleteTimeline(before)
		advanceUntilIdle()
		assertEquals(TimelineSwitcher.Dialog.Delete(before, "Before", 2), component.switcher.value.dialog)
		component.confirmDeleteTimeline()
		advanceUntilIdle()

		assertNull(timelineService.definition(before))
		assertEquals(TimelineSelection.Main, component.selection.value)
		assertNull(component.switcher.value.dialog)
	}

	@Test
	fun `main offers no delete`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestDeleteTimeline(TimelineDef.MAIN_ID)
		advanceUntilIdle()

		assertNull(component.switcher.value.dialog)
	}

	// endregion
}
