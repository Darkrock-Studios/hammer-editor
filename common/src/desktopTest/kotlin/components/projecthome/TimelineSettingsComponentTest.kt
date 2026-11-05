package components.projecthome

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings.ModeSwitchPrompt
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettingsComponent
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSwitcher
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineDefResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import components.timeline.TimelineComponentTestBase
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineSettingsComponentTest : TimelineComponentTestBase() {

	private fun newComponent() = TimelineSettingsComponent(componentContext = context, projectDef = projectDef)

	private fun date(vararg rungs: Pair<String, Long>) = StoryDate(linkedMapOf(*rungs))

	private suspend fun createEvent(title: String, start: StoryDate? = null, timeline: String? = null): Int =
		(timelineService.createEvent(TimelineEventDraft(title = title, start = start, timeline = timeline)) as TimelineResult.Ok).event!!.id

	private suspend fun newTimeline(name: String, mode: TimelineMode = TimelineMode.MANUAL): TimelineDef =
		(timelineService.createTimeline(name, mode, CalendarDef().takeIf { mode == TimelineMode.DATED }) as TimelineDefResult.Ok).def

	@Test
	fun `requesting dated opens the prompt with a day selected`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestMode(TimelineMode.DATED)

		val prompt = assertIs<ModeSwitchPrompt.ToDated>(component.state.value.pendingModeSwitch)
		assertEquals(CalendarDef(CalendarPreset.STORY), prompt.calendar)
		assertEquals("day", prompt.selectedUnit)
		assertEquals(listOf("year", "day", "hour", "minute"), prompt.units.map { it.name })

		component.cancelModeSwitch()
		assertNull(component.state.value.pendingModeSwitch)
		assertEquals(TimelineMode.MANUAL, timelineService.definition(TimelineDef.MAIN_ID)?.mode)
	}

	@Test
	fun `a story told in hours offers its units from the hour`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.updateDefinition(TimelineDef.MAIN_ID) { it.copy(calendar = CalendarDef(coarsestUnit = "hour")) }
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestMode(TimelineMode.DATED)

		val prompt = assertIs<ModeSwitchPrompt.ToDated>(component.state.value.pendingModeSwitch)
		assertEquals("hour", prompt.selectedUnit)
		assertEquals(listOf("hour", "minute"), prompt.units.map { it.name })
	}

	@Test
	fun `a gregorian calendar spaces by the day unless told otherwise`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.updateDefinition(TimelineDef.MAIN_ID) { it.copy(calendar = CalendarDef(CalendarPreset.GREGORIAN)) }
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestMode(TimelineMode.DATED)

		val prompt = assertIs<ModeSwitchPrompt.ToDated>(component.state.value.pendingModeSwitch)
		assertEquals("day", prompt.selectedUnit)
	}

	@Test
	fun `confirming dated spaces the entries evenly in their order`() = runTest(mainTestDispatcher) {
		givenProject()
		val first = createEvent("One")
		val second = createEvent("Two")
		val third = createEvent("Three")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestMode(TimelineMode.DATED)
		component.setSpacingUnit("day")
		component.confirmModeSwitch()
		advanceUntilIdle()

		val def = timelineService.definition(TimelineDef.MAIN_ID)!!
		assertEquals(TimelineMode.DATED, def.mode)
		assertEquals(CalendarDef(), def.calendar)
		assertEquals(TimelineMode.DATED, component.state.value.def.mode)
		assertNull(component.state.value.pendingModeSwitch)
		val starts = timeLineRepository.currentEvents().associate { it.id to it.start }
		assertEquals(date("year" to 0, "day" to 0), starts[first])
		assertEquals(date("year" to 0, "day" to 1), starts[second])
		assertEquals(date("year" to 0, "day" to 2), starts[third])
	}

	@Test
	fun `a gregorian switch starts at the first of January, year one`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.updateDefinition(TimelineDef.MAIN_ID) { it.copy(calendar = CalendarDef(CalendarPreset.GREGORIAN)) }
		val first = createEvent("One")
		val second = createEvent("Two")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestMode(TimelineMode.DATED)
		component.setSpacingUnit("month")
		component.confirmModeSwitch()
		advanceUntilIdle()

		val starts = timeLineRepository.currentEvents().associate { it.id to it.start }
		assertEquals(date("year" to 1, "month" to 1), starts[first])
		assertEquals(date("year" to 1, "month" to 2), starts[second])
	}

	@Test
	fun `switching to manual freezes the date order`() = runTest(mainTestDispatcher) {
		givenProject()
		val late = createEvent("Late")
		val early = createEvent("Early")
		timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef())
		timelineService.setDates(late, date("year" to 9), null)
		timelineService.setDates(early, date("year" to 2), null)
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestMode(TimelineMode.MANUAL)
		assertIs<ModeSwitchPrompt.ToManual>(component.state.value.pendingModeSwitch)
		component.confirmModeSwitch()
		advanceUntilIdle()

		assertEquals(TimelineMode.MANUAL, timelineService.definition(TimelineDef.MAIN_ID)?.mode)
		val ordered = timeLineRepository.currentEvents().sortedBy { it.order }
		assertEquals(listOf(early, late), ordered.map { it.id })
		assertEquals(date("year" to 9), ordered.last().start, "Dates stay")
	}

	@Test
	fun `switching to manual labels the dates in the app's words`() = runTest(mainTestDispatcher) {
		givenProject()
		val labelled = createEvent("Feast")
		val unlabelled = createEvent("Storm")
		timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef(CalendarPreset.GREGORIAN))
		timelineService.setDates(unlabelled, date("year" to 1811, "month" to 11), null)
		timelineService.setLabel(labelled, "Midsummer")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestMode(TimelineMode.MANUAL)
		component.confirmModeSwitch()
		advanceUntilIdle()

		val labels = timeLineRepository.currentEvents().associate { it.id to it.label }
		assertEquals("<timeline_month_11> 1811", labels[unlabelled])
		assertEquals("Midsummer", labels[labelled])
	}

	@Test
	fun `a preset switch that loses dates waits for confirmation and keeps them as labels`() =
		runTest(mainTestDispatcher) {
			givenProject()
			val event = createEvent("Storm")
			timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef())
			timelineService.setDates(event, date("year" to 5, "day" to 10), null)
			val component = newComponent()
			context.resume()
			advanceUntilIdle()

			component.setPreset(CalendarPreset.GREGORIAN)
			advanceUntilIdle()

			assertEquals(CalendarDef(CalendarPreset.GREGORIAN), component.state.value.pendingCalendar)
			assertEquals(1, component.state.value.datesLostOnPresetSwitch)
			assertEquals(CalendarPreset.STORY, timelineService.definition(TimelineDef.MAIN_ID)?.calendar?.preset)

			component.confirmPresetSwitch()
			advanceUntilIdle()

			assertEquals(CalendarPreset.GREGORIAN, timelineService.definition(TimelineDef.MAIN_ID)?.calendar?.preset)
			assertNull(component.state.value.pendingCalendar)
			val stored = timeLineRepository.currentEvents().single()
			assertNull(stored.start)
			assertEquals("<timeline_unit_year> 5 · <timeline_unit_day> 10", stored.label, "In the app's words")
		}

	@Test
	fun `a preset switch that loses nothing applies at once`() = runTest(mainTestDispatcher) {
		givenProject()
		createEvent("Storm")
		timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setPreset(CalendarPreset.GREGORIAN)
		advanceUntilIdle()

		assertNull(component.state.value.pendingCalendar)
		assertEquals(CalendarPreset.GREGORIAN, timelineService.definition(TimelineDef.MAIN_ID)?.calendar?.preset)
		assertEquals(CalendarPreset.GREGORIAN, component.state.value.def.calendar?.preset)
	}

	@Test
	fun `a cancelled preset switch changes nothing`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = createEvent("Storm")
		timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef())
		timelineService.setDates(event, date("year" to 5, "day" to 10), null)
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setPreset(CalendarPreset.GREGORIAN)
		advanceUntilIdle()
		component.cancelPresetSwitch()
		advanceUntilIdle()

		assertNull(component.state.value.pendingCalendar)
		assertEquals(CalendarPreset.STORY, timelineService.definition(TimelineDef.MAIN_ID)?.calendar?.preset)
		assertEquals(date("year" to 5, "day" to 10), timeLineRepository.currentEvents().single().start)
	}

	@Test
	fun `the epoch and the coarsest unit write through`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setEpochName("  The Landing ")
		advanceUntilIdle()
		component.setCoarsestUnit("hour")
		advanceUntilIdle()

		assertEquals(CalendarDef(epochName = "The Landing", coarsestUnit = "hour"), timelineService.definition(TimelineDef.MAIN_ID)?.calendar)

		component.setCoarsestUnit("year")
		advanceUntilIdle()
		assertNull(timelineService.definition(TimelineDef.MAIN_ID)?.calendar?.coarsestUnit)
	}

	@Test
	fun `typing an epoch writes once, after the typing pauses`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		val stored = mutableListOf<String?>()
		backgroundScope.launch {
			timelineService.timelineFlow(TimelineDef.MAIN_ID).map { it.def.calendar?.epochName }.distinctUntilChanged().toList(stored)
		}
		runCurrent()

		component.setEpochName("L")
		advanceTimeBy(100)
		component.setEpochName("La")
		advanceTimeBy(100)
		component.setEpochName("Lan")
		advanceTimeBy(599)
		runCurrent()
		assertNull(timelineService.definition(TimelineDef.MAIN_ID)?.calendar?.epochName, "Nothing is written while typing")

		advanceUntilIdle()

		assertEquals("Lan", timelineService.definition(TimelineDef.MAIN_ID)?.calendar?.epochName)
		assertEquals(listOf(null, "Lan"), stored)
	}

	@Test
	fun `an epoch still waiting is written when the settings close`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setEpochName("Landing")
		context.destroy()
		advanceUntilIdle()

		assertEquals("Landing", timelineService.definition(TimelineDef.MAIN_ID)?.calendar?.epochName)
	}

	@Test
	fun `lane order writes through`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.createEvent(TimelineEventDraft(title = "Fall", lane = "Alice"))
		timelineService.createEvent(TimelineEventDraft(title = "Croquet", lane = "Queen"))
		val component = newComponent()
		context.resume()
		advanceUntilIdle()
		assertEquals(listOf("Alice", "Queen"), component.state.value.lanes)

		component.setLaneOrder(listOf("Queen", "Alice"))
		advanceUntilIdle()

		assertEquals(listOf("Queen", "Alice"), timelineService.definition(TimelineDef.MAIN_ID)?.laneOrder)
		assertEquals(listOf("Queen", "Alice"), component.state.value.lanes)
	}

	// region Timelines

	@Test
	fun `every timeline is listed with its count, and main is edited first`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		createEvent("One")
		createEvent("Two", timeline = before.id)
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		assertEquals(
			listOf(TimelineSummary(TimelineDef.DEFAULT, 1), TimelineSummary(before, 1)),
			component.state.value.timelines,
		)
		assertEquals(TimelineDef.MAIN_ID, component.state.value.editingId)
		assertEquals(TimelineDef.DEFAULT, component.state.value.def)
		assertFalse(component.state.value.editorOpen)
	}

	@Test
	fun `editing a timeline opens the editor on it, and closing drops what was pending`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.editTimeline(before.id)
		advanceUntilIdle()
		assertTrue(component.state.value.editorOpen)
		assertEquals(before, component.state.value.def)

		component.requestMode(TimelineMode.DATED)
		component.closeEditor()

		assertFalse(component.state.value.editorOpen)
		assertNull(component.state.value.pendingModeSwitch)
		assertEquals(TimelineMode.MANUAL, timelineService.definition(before.id)?.mode)
	}

	@Test
	fun `a non-main timeline is edited like main, and main is left alone`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		val onMain = createEvent("On main")
		val first = createEvent("One", timeline = before.id)
		val second = createEvent("Two", timeline = before.id)
		createEvent("Lane", timeline = before.id).let { timelineService.setLane(it, "Pike") }
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.editTimeline(before.id)
		advanceUntilIdle()
		assertEquals(before, component.state.value.def)
		assertEquals(listOf("Pike"), component.state.value.lanes)

		component.requestMode(TimelineMode.DATED)
		val prompt = assertIs<ModeSwitchPrompt.ToDated>(component.state.value.pendingModeSwitch)
		assertEquals("Before", prompt.timeline.name)
		assertEquals(3, prompt.eventCount)
		component.setSpacingUnit("day")
		component.confirmModeSwitch()
		advanceUntilIdle()
		component.setLaneOrder(listOf("Pike", "Hayes"))
		advanceUntilIdle()

		val edited = timelineService.definition(before.id)!!
		assertEquals(TimelineMode.DATED, edited.mode)
		assertEquals(listOf("Pike", "Hayes"), edited.laneOrder)
		assertEquals(TimelineMode.DATED, component.state.value.def.mode)
		assertEquals(TimelineDef.DEFAULT, timelineService.definition(TimelineDef.MAIN_ID))
		val starts = timeLineRepository.currentEvents().associate { it.id to it.start }
		assertEquals(date("year" to 0, "day" to 0), starts[first])
		assertEquals(date("year" to 0, "day" to 1), starts[second])
		assertNull(starts[onMain])
	}

	@Test
	fun `a preset warning counts only the edited timeline's dates`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef())
		val before = newTimeline("Before", TimelineMode.DATED)
		createEvent("Main", start = date("year" to 5, "day" to 3))
		createEvent("Before", start = date("year" to 5, "day" to 3), timeline = before.id)
		val component = newComponent()
		context.resume()
		component.editTimeline(before.id)
		advanceUntilIdle()

		component.setPreset(CalendarPreset.GREGORIAN)
		advanceUntilIdle()

		assertEquals(1, component.state.value.datesLostOnPresetSwitch)
		assertEquals(before.id, component.state.value.editingId)
		component.confirmPresetSwitch()
		advanceUntilIdle()

		assertEquals(CalendarPreset.GREGORIAN, timelineService.definition(before.id)?.calendar?.preset)
		assertEquals(CalendarDef(), timelineService.definition(TimelineDef.MAIN_ID)?.calendar)
	}

	@Test
	fun `a preset warning counted after another timeline opened is dropped`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef())
		val before = newTimeline("Before", TimelineMode.DATED)
		createEvent("Before", start = date("year" to 5, "day" to 3), timeline = before.id)
		val component = newComponent()
		context.resume()
		component.editTimeline(before.id)
		advanceUntilIdle()

		component.setPreset(CalendarPreset.GREGORIAN)
		component.editTimeline(TimelineDef.MAIN_ID)
		advanceUntilIdle()

		assertNull(component.state.value.pendingCalendar)
		assertEquals(CalendarDef(), timelineService.definition(before.id)?.calendar)
	}

	@Test
	fun `an epoch typed before a switch is written to the timeline it was typed for`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef())
		val before = newTimeline("Before", TimelineMode.DATED)
		val component = newComponent()
		context.resume()
		component.editTimeline(before.id)
		advanceUntilIdle()

		component.setEpochName("The letter")
		advanceTimeBy(100)
		component.editTimeline(TimelineDef.MAIN_ID)
		advanceUntilIdle()

		assertEquals("The letter", timelineService.definition(before.id)?.calendar?.epochName)
		assertNull(timelineService.definition(TimelineDef.MAIN_ID)?.calendar?.epochName)
	}

	@Test
	fun `an epoch waiting for one timeline is not lost to typing on another`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(TimelineDef.MAIN_ID, CalendarDef())
		val before = newTimeline("Before", TimelineMode.DATED)
		val component = newComponent()
		context.resume()
		component.editTimeline(before.id)
		advanceUntilIdle()

		component.setEpochName("The letter")
		component.editTimeline(TimelineDef.MAIN_ID)
		component.setEpochName("The dinner bell")
		advanceUntilIdle()

		assertEquals("The letter", timelineService.definition(before.id)?.calendar?.epochName)
		assertEquals("The dinner bell", timelineService.definition(TimelineDef.MAIN_ID)?.calendar?.epochName)
	}

	@Test
	fun `requesting a new timeline opens an empty create dialog`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestCreate()

		assertEquals(TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Create()), component.switcher.value)
	}

	@Test
	fun `a created timeline closes the dialog and leaves the editor shut`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestCreate()
		component.createTimeline("Before", TimelineMode.DATED, CalendarPreset.GREGORIAN)
		advanceUntilIdle()

		val created = component.state.value.timelines.single { it.def.name == "Before" }.def
		assertEquals(TimelineSwitcher(), component.switcher.value)
		assertFalse(component.state.value.editorOpen)
		assertEquals(CalendarDef(CalendarPreset.GREGORIAN), created.calendar)
	}

	@Test
	fun `a refused name keeps the dialog open with its own error`() = runTest(mainTestDispatcher) {
		givenProject()
		newTimeline("Before")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestCreate()
		component.createTimeline("before", TimelineMode.MANUAL, CalendarPreset.STORY)
		advanceUntilIdle()

		val switcher = component.switcher.value
		assertEquals(TimelineSwitcher.Dialog.Create("before", TimelineMode.MANUAL, CalendarPreset.STORY), switcher.dialog)
		assertEquals(TimeLineEventError.TIMELINE_NAME_INVALID, switcher.error)
		assertNull(component.state.value.error)
		assertEquals(TimelineDef.MAIN_ID, component.state.value.editingId)
	}

	@Test
	fun `dismissing a refused dialog clears its error`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestCreate()
		component.createTimeline("Main", TimelineMode.MANUAL, CalendarPreset.STORY)
		advanceUntilIdle()
		assertEquals(TimeLineEventError.TIMELINE_NAME_INVALID, component.switcher.value.error)

		component.dismissDialog()

		assertEquals(TimelineSwitcher(), component.switcher.value)
		assertNull(component.state.value.error)
	}

	@Test
	fun `a refusal for a dialog replaced while it ran is dropped`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestCreate()
		component.createTimeline("Main", TimelineMode.MANUAL, CalendarPreset.STORY)
		component.requestRename(before.id)
		advanceUntilIdle()

		assertEquals(
			TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Rename(before.id, "Before")),
			component.switcher.value,
		)
	}

	@Test
	fun `a timeline is renamed and the dialog closes`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestRename(before.id)
		assertEquals(TimelineSwitcher.Dialog.Rename(before.id, "Before"), component.switcher.value.dialog)
		component.renameTimeline("Before the evening")
		advanceUntilIdle()

		assertEquals("Before the evening", component.state.value.timelines.last().def.name)
		assertEquals(TimelineSwitcher(), component.switcher.value)
	}

	@Test
	fun `a refused rename keeps the dialog open with its error`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestRename(before.id)
		component.renameTimeline("main")
		advanceUntilIdle()

		val switcher = component.switcher.value
		assertEquals(TimelineSwitcher.Dialog.Rename(before.id, "main"), switcher.dialog)
		assertEquals(TimeLineEventError.TIMELINE_NAME_INVALID, switcher.error)
		assertEquals("Before", timelineService.definition(before.id)?.name)
	}

	@Test
	fun `a timeline asked for as the screen opens is edited once the list loads`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before the evening")
		val component = newComponent()
		context.resume()

		component.editTimeline(before.id)
		advanceUntilIdle()

		assertEquals(before.id, component.state.value.editingId)
		assertEquals(before, component.state.value.def)
	}

	@Test
	fun `editing a timeline that is gone edits main`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.editTimeline("gone0001")
		advanceUntilIdle()

		assertEquals(TimelineDef.MAIN_ID, component.state.value.editingId)
	}

	@Test
	fun `deleting the edited timeline asks first, then closes the editor and edits main`() = runTest(mainTestDispatcher) {
		givenProject()
		val before = newTimeline("Before")
		createEvent("One", timeline = before.id)
		val component = newComponent()
		context.resume()
		component.editTimeline(before.id)
		advanceUntilIdle()

		component.requestDelete(TimelineDef.MAIN_ID)
		assertNull(component.switcher.value.dialog)
		component.requestDelete(before.id)
		assertEquals(TimelineSwitcher.Dialog.Delete(before.id, "Before", 1), component.switcher.value.dialog)
		component.dismissDialog()
		assertNull(component.switcher.value.dialog)

		component.requestDelete(before.id)
		component.confirmDelete()
		advanceUntilIdle()

		assertNull(timelineService.definition(before.id))
		assertEquals(TimelineSwitcher(), component.switcher.value)
		assertFalse(component.state.value.editorOpen)
		assertEquals(TimelineDef.MAIN_ID, component.state.value.editingId)
		assertEquals(TimelineDef.DEFAULT, component.state.value.def)
		assertEquals(listOf(TimelineDef.MAIN_ID), component.state.value.timelines.map { it.def.id })
	}

	// endregion
}
