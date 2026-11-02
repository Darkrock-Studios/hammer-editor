package components.projecthome

import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings.ModeSwitchPrompt
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettingsComponent
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineEventDraft
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import components.timeline.TimelineComponentTestBase
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TimelineSettingsComponentTest : TimelineComponentTestBase() {

	private fun newComponent() = TimelineSettingsComponent(componentContext = context, projectDef = projectDef)

	private fun date(vararg rungs: Pair<String, Long>) = StoryDate(linkedMapOf(*rungs))

	private suspend fun createEvent(title: String, start: StoryDate? = null): Int =
		(timelineService.createEvent(TimelineEventDraft(title = title, start = start)) as TimelineResult.Ok).event!!.id

	@Test
	fun `requesting dated opens the prompt with the coarsest unit selected`() = runTest(mainTestDispatcher) {
		givenProject()
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestMode(TimelineMode.DATED)

		val prompt = assertIs<ModeSwitchPrompt.ToDated>(component.state.value.pendingModeSwitch)
		assertEquals(CalendarDef(CalendarPreset.STORY), prompt.calendar)
		assertEquals("year", prompt.selectedUnit)
		assertEquals(listOf("year", "day", "hour", "minute"), prompt.units.map { it.name })

		component.cancelModeSwitch()
		assertNull(component.state.value.pendingModeSwitch)
		assertEquals(TimelineMode.MANUAL, timelineService.definition().mode)
	}

	@Test
	fun `a story told in hours offers its units from the hour`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.updateDefinition { it.copy(calendar = CalendarDef(coarsestUnit = "hour")) }
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestMode(TimelineMode.DATED)

		val prompt = assertIs<ModeSwitchPrompt.ToDated>(component.state.value.pendingModeSwitch)
		assertEquals("hour", prompt.selectedUnit)
		assertEquals(listOf("hour", "minute"), prompt.units.map { it.name })
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

		val def = timelineService.definition()
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
		timelineService.updateDefinition { it.copy(calendar = CalendarDef(CalendarPreset.GREGORIAN)) }
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
		timelineService.switchToDated(CalendarDef())
		timelineService.setDates(late, date("year" to 9), null)
		timelineService.setDates(early, date("year" to 2), null)
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.requestMode(TimelineMode.MANUAL)
		assertEquals(ModeSwitchPrompt.ToManual, component.state.value.pendingModeSwitch)
		component.confirmModeSwitch()
		advanceUntilIdle()

		assertEquals(TimelineMode.MANUAL, timelineService.definition().mode)
		val ordered = timeLineRepository.currentEvents().sortedBy { it.order }
		assertEquals(listOf(early, late), ordered.map { it.id })
		assertEquals(date("year" to 9), ordered.last().start, "Dates stay")
	}

	@Test
	fun `a preset switch that loses dates waits for confirmation and keeps them as labels`() =
		runTest(mainTestDispatcher) {
			givenProject()
			val event = createEvent("Storm")
			timelineService.switchToDated(CalendarDef())
			timelineService.setDates(event, date("year" to 5, "day" to 10), null)
			val component = newComponent()
			context.resume()
			advanceUntilIdle()

			component.setPreset(CalendarPreset.GREGORIAN)
			advanceUntilIdle()

			assertEquals(CalendarDef(CalendarPreset.GREGORIAN), component.state.value.pendingCalendar)
			assertEquals(1, component.state.value.datesLostOnPresetSwitch)
			assertEquals(CalendarPreset.STORY, timelineService.definition().calendar?.preset)

			component.confirmPresetSwitch()
			advanceUntilIdle()

			assertEquals(CalendarPreset.GREGORIAN, timelineService.definition().calendar?.preset)
			assertNull(component.state.value.pendingCalendar)
			val stored = timeLineRepository.currentEvents().single()
			assertNull(stored.start)
			assertEquals("year=5 day=10", stored.label)
		}

	@Test
	fun `a preset switch that loses nothing applies at once`() = runTest(mainTestDispatcher) {
		givenProject()
		createEvent("Storm")
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setPreset(CalendarPreset.GREGORIAN)
		advanceUntilIdle()

		assertNull(component.state.value.pendingCalendar)
		assertEquals(CalendarPreset.GREGORIAN, timelineService.definition().calendar?.preset)
		assertEquals(CalendarPreset.GREGORIAN, component.state.value.def.calendar?.preset)
	}

	@Test
	fun `a cancelled preset switch changes nothing`() = runTest(mainTestDispatcher) {
		givenProject()
		val event = createEvent("Storm")
		timelineService.switchToDated(CalendarDef())
		timelineService.setDates(event, date("year" to 5, "day" to 10), null)
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setPreset(CalendarPreset.GREGORIAN)
		advanceUntilIdle()
		component.cancelPresetSwitch()
		advanceUntilIdle()

		assertNull(component.state.value.pendingCalendar)
		assertEquals(CalendarPreset.STORY, timelineService.definition().calendar?.preset)
		assertEquals(date("year" to 5, "day" to 10), timeLineRepository.currentEvents().single().start)
	}

	@Test
	fun `the epoch and the coarsest unit write through`() = runTest(mainTestDispatcher) {
		givenProject()
		timelineService.switchToDated(CalendarDef())
		val component = newComponent()
		context.resume()
		advanceUntilIdle()

		component.setEpochName("  The Landing ")
		advanceUntilIdle()
		component.setCoarsestUnit("hour")
		advanceUntilIdle()

		assertEquals(CalendarDef(epochName = "The Landing", coarsestUnit = "hour"), timelineService.definition().calendar)

		component.setCoarsestUnit("year")
		advanceUntilIdle()
		assertNull(timelineService.definition().calendar?.coarsestUnit)
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

		assertEquals(listOf("Queen", "Alice"), timelineService.definition().laneOrder)
		assertEquals(listOf("Queen", "Alice"), component.state.value.lanes)
	}
}
