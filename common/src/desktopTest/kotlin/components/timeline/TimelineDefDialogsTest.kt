package components.timeline

import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineDefDialogs
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSwitcher
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class TimelineDefDialogsTest : TimelineComponentTestBase() {

	@Test
	fun `a create that finishes after another dialog opened leaves that dialog open`() = runTest(mainTestDispatcher) {
		givenProject()
		val main = timelineService.definition(TimelineDef.MAIN_ID)!!
		val created = mutableListOf<String>()
		val dialogs = TimelineDefDialogs(timelineService, this, mainTestDispatcher, onCreated = { created += it.name })

		dialogs.create("Before", TimelineMode.MANUAL, CalendarPreset.STORY)
		dialogs.requestRename(main)
		advanceUntilIdle()

		assertEquals(TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Rename(main.id, main.name)), dialogs.state.value)
		assertEquals(listOf("Before"), created)
		assertEquals(listOf(main.name, "Before"), timelineService.timelines.first().map { it.name })
	}

	@Test
	fun `a refusal that lands after another dialog opened leaves that dialog without an error`() = runTest(mainTestDispatcher) {
		givenProject()
		val main = timelineService.definition(TimelineDef.MAIN_ID)!!
		val dialogs = TimelineDefDialogs(timelineService, this, mainTestDispatcher)

		dialogs.create(main.name, TimelineMode.MANUAL, CalendarPreset.STORY)
		dialogs.requestRename(main)
		advanceUntilIdle()

		assertEquals(TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Rename(main.id, main.name)), dialogs.state.value)
	}

	@Test
	fun `a refusal on the open dialog keeps it open with the reason`() = runTest(mainTestDispatcher) {
		givenProject()
		val main = timelineService.definition(TimelineDef.MAIN_ID)!!
		val dialogs = TimelineDefDialogs(timelineService, this, mainTestDispatcher)

		dialogs.create(main.name, TimelineMode.MANUAL, CalendarPreset.STORY)
		advanceUntilIdle()

		assertEquals(
			TimelineSwitcher(
				dialog = TimelineSwitcher.Dialog.Create(main.name, TimelineMode.MANUAL, CalendarPreset.STORY),
				error = TimeLineEventError.TIMELINE_NAME_INVALID,
			),
			dialogs.state.value,
		)
	}
}
