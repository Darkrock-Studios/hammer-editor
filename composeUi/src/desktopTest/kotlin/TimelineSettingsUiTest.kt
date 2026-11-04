import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings.ModeSwitchPrompt
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineSummary
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.entryUnits
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.projecthome.TIMELINE_MODE_DIALOG_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.TIMELINE_MODE_DIALOG_COUNT_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.TIMELINE_SETTINGS_EDITING_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.TIMELINE_SETTINGS_NEW_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.timelineSettingsDeleteTag
import com.darkrockstudios.apps.hammer.common.projecthome.timelineSettingsRenameTag
import com.darkrockstudios.apps.hammer.common.projecthome.timelineSettingsRowTag
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_DELETE_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_NEW_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_NEW_NAME_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_RENAME_CONFIRM_TAG
import com.darkrockstudios.apps.hammer.common.timeline.TIME_LINE_RENAME_NAME_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.TIMELINE_MODE_DIALOG_UNIT_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.TIMELINE_SETTINGS_EPOCH_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.TIMELINE_SETTINGS_PRESET_WARNING_TAG
import com.darkrockstudios.apps.hammer.common.projecthome.TimelineSettingsUi
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class TimelineSettingsUiTest : BaseTest() {
	@get:Rule
	val compose = createComposeRule()

	private class FakeSettings(initial: TimelineSettings.State) : TimelineSettings {
		private val _state = MutableValue(initial)
		override val state: Value<TimelineSettings.State> = _state
		var confirmed = 0
		var presetConfirmed = 0
		var deleteConfirmed = 0
		val selected = mutableListOf<String>()
		val created = mutableListOf<String>()
		val renamed = mutableListOf<Pair<String, String>>()

		/** What [createTimeline] answers with; null creates the timeline and edits it. */
		var createRefusal: TimeLineEventError? = null

		override fun requestMode(mode: TimelineMode) {
			val current = _state.value
			if (mode == current.def.mode) return
			val count = current.timelines.firstOrNull { it.def.id == current.def.id }?.eventCount ?: 0
			val prompt = when (mode) {
				TimelineMode.DATED -> {
					val calendar = current.def.calendar ?: CalendarDef()
					val units = entryUnits(calendar)
					ModeSwitchPrompt.ToDated(units, "day", calendar, current.def, count)
				}

				TimelineMode.MANUAL -> ModeSwitchPrompt.ToManual(current.def, count)
			}
			_state.value = _state.value.copy(pendingModeSwitch = prompt)
		}

		override fun setSpacingUnit(unit: String) {}
		override fun confirmModeSwitch() {
			confirmed++
			_state.value = _state.value.copy(pendingModeSwitch = null)
		}

		override fun cancelModeSwitch() {
			_state.value = _state.value.copy(pendingModeSwitch = null)
		}

		override fun setPreset(preset: CalendarPreset) {}
		override fun setCoarsestUnit(unit: String?) {}
		override fun confirmPresetSwitch() {
			presetConfirmed++
		}

		override fun cancelPresetSwitch() {}
		override fun setEpochName(name: String) {}
		override fun setLaneOrder(order: List<String>) {}
		override fun selectTimeline(id: String) {
			selected += id
		}

		override fun createTimeline(name: String, mode: TimelineMode, preset: CalendarPreset) {
			created += name
			val refusal = createRefusal
			if (refusal != null) {
				_state.value = _state.value.copy(error = refusal)
				return
			}
			val def = TimelineDef(id = "new${created.size}", name = name, mode = mode)
			_state.value = _state.value.copy(
				timelines = _state.value.timelines + TimelineSummary(def, 0),
				editingId = def.id,
				def = def,
				error = null,
			)
		}

		override fun renameTimeline(id: String, name: String) {
			renamed += id to name
			_state.value = _state.value.copy(
				timelines = _state.value.timelines.map {
					if (it.def.id == id) it.copy(def = it.def.copy(name = name)) else it
				},
			)
		}

		override fun requestDelete(id: String) {
			_state.value = _state.value.copy(pendingDelete = _state.value.timelines.first { it.def.id == id })
		}

		override fun confirmDelete() {
			deleteConfirmed++
			_state.value = _state.value.copy(pendingDelete = null)
		}

		override fun cancelDelete() {
			_state.value = _state.value.copy(pendingDelete = null)
		}
	}

	private val main = TimelineSummary(TimelineDef.DEFAULT, 4)
	private val backstory = TimelineSummary(
		TimelineDef(
			id = "backstory",
			name = "Before the evening",
			mode = TimelineMode.DATED,
			calendar = CalendarDef(CalendarPreset.GREGORIAN),
		),
		13,
	)
	private val scratch = TimelineSummary(TimelineDef(id = "scratch", name = "Scratch"), 1)

	/** Three timelines, editing [editing]. */
	private fun threeTimelines(editing: TimelineSummary = main) = FakeSettings(
		TimelineSettings.State(
			timelines = listOf(main, backstory, scratch),
			editingId = editing.def.id,
			def = editing.def,
		)
	)

	private fun show(component: TimelineSettings) {
		compose.setContent {
			KoinApplicationPreview {
				Box(modifier = Modifier.width(720.dp)) {
					TimelineSettingsUi(component = component, section = 6)
				}
			}
		}
	}

	@Test
	fun `Picking Dated opens the dialog with Space evenly by the preselected unit`() {
		val component = FakeSettings(TimelineSettings.State())
		show(component)

		compose.onNodeWithText("DATED").performClick()
		compose.waitForIdle()

		compose.onNodeWithText("Switch to a dated timeline").assertIsDisplayed()
		compose.onNodeWithText("SPACE EVENLY BY").assertIsDisplayed()
		compose.onNodeWithTag(TIMELINE_MODE_DIALOG_UNIT_TAG).assertIsDisplayed()
		compose.onNodeWithText("Day").assertIsDisplayed()

		compose.onNodeWithTag(TIMELINE_MODE_DIALOG_CONFIRM_TAG).performClick()
		compose.waitForIdle()
		assertEquals(1, component.confirmed)
	}

	@Test
	fun `A dated story calendar shows its epoch and coarsest unit`() {
		val def = TimelineDef.DEFAULT.copy(
			mode = TimelineMode.DATED,
			calendar = CalendarDef(epochName = "Landing", coarsestUnit = "day"),
		)
		show(FakeSettings(TimelineSettings.State(def = def, lanes = listOf("Alice", "Queen"))))

		compose.onNodeWithTag(TIMELINE_SETTINGS_EPOCH_TAG).assertExists()
		compose.onNodeWithText("Day").assertIsDisplayed()
		compose.onNodeWithText("Alice").assertIsDisplayed()
		compose.onNodeWithText("Queen").assertIsDisplayed()
	}

	@Test
	fun `A calendar switch that loses dates asks first`() {
		val def = TimelineDef.DEFAULT.copy(mode = TimelineMode.DATED, calendar = CalendarDef())
		val component = FakeSettings(
			TimelineSettings.State(
				def = def,
				pendingCalendar = CalendarDef(CalendarPreset.GREGORIAN),
				datesLostOnPresetSwitch = 2,
			)
		)
		show(component)

		compose.onNodeWithTag(TIMELINE_SETTINGS_PRESET_WARNING_TAG).assertIsDisplayed()
		compose.onNodeWithText("2 entries keep their date as a label").assertIsDisplayed()

		compose.onNodeWithText("Switch calendar").performClick()
		compose.waitForIdle()
		assertEquals(1, component.presetConfirmed)
	}

	@Test
	fun `Lists every timeline with its mode and event count, and main cannot be deleted`() {
		show(threeTimelines())

		compose.onNodeWithText("TIMELINES").assertIsDisplayed()
		compose.onNodeWithTag(timelineSettingsRowTag("main")).assertIsDisplayed()
		compose.onNodeWithText("MANUAL · 4 EVENTS").assertIsDisplayed()
		compose.onNodeWithText("DATED · 13 EVENTS").assertIsDisplayed()
		compose.onNodeWithText("MANUAL · 1 EVENT").assertIsDisplayed()
		compose.onNodeWithTag(timelineSettingsRenameTag("main")).assertIsDisplayed()
		compose.onNodeWithTag(timelineSettingsDeleteTag("main")).assertDoesNotExist()
		compose.onNodeWithTag(timelineSettingsDeleteTag("backstory")).assertIsDisplayed()
		compose.onNodeWithTag(TIMELINE_SETTINGS_NEW_TAG).assertIsDisplayed()
	}

	@Test
	fun `With one timeline there is nothing to pick for editing`() {
		show(FakeSettings(TimelineSettings.State(timelines = listOf(main))))

		compose.onNodeWithTag(timelineSettingsRowTag("main")).assertIsDisplayed()
		compose.onNodeWithTag(TIMELINE_SETTINGS_EDITING_TAG).assertDoesNotExist()
	}

	@Test
	fun `The Editing dropdown picks the timeline the settings below edit`() {
		val component = threeTimelines()
		show(component)

		compose.onNodeWithText("EDITING").assertIsDisplayed()
		compose.onNode(hasText("Main") and hasAnyAncestor(hasTestTag(TIMELINE_SETTINGS_EDITING_TAG))).performClick()
		compose.onAllNodesWithText("Before the evening").onLast().performClick()
		compose.waitForIdle()

		assertEquals(listOf("backstory"), component.selected)
	}

	@Test
	fun `Deleting a timeline asks first, counting its events`() {
		val component = threeTimelines()
		show(component)

		compose.onNodeWithTag(timelineSettingsDeleteTag("backstory")).performClick()
		compose.waitForIdle()

		compose.onNodeWithText("Delete “Before the evening”?").assertIsDisplayed()
		compose.onNodeWithText("Its 13 events are deleted with it, on every synced device.").assertIsDisplayed()
		assertEquals(0, component.deleteConfirmed)

		compose.onNodeWithTag(TIME_LINE_DELETE_CONFIRM_TAG).performClick()
		compose.waitForIdle()

		assertEquals(1, component.deleteConfirmed)
		compose.onNodeWithText("Delete “Before the evening”?").assertDoesNotExist()
	}

	@Test
	fun `A new timeline is created from the list, and the dialog closes once it exists`() {
		val component = threeTimelines()
		show(component)

		compose.onNodeWithTag(TIMELINE_SETTINGS_NEW_TAG).performClick()
		compose.waitForIdle()
		compose.onNodeWithTag(TIME_LINE_NEW_NAME_TAG).performTextInput("Aftermath")
		compose.onNodeWithTag(TIME_LINE_NEW_CONFIRM_TAG).performClick()
		compose.waitForIdle()

		assertEquals(listOf("Aftermath"), component.created)
		compose.onNodeWithTag(TIME_LINE_NEW_NAME_TAG).assertDoesNotExist()
		compose.onNodeWithText("MANUAL · 0 EVENTS").assertIsDisplayed()
	}

	@Test
	fun `A refused name keeps the new timeline dialog open`() {
		val component = threeTimelines().apply { createRefusal = TimeLineEventError.TIMELINE_NAME_INVALID }
		show(component)

		compose.onNodeWithTag(TIMELINE_SETTINGS_NEW_TAG).performClick()
		compose.waitForIdle()
		compose.onNodeWithTag(TIME_LINE_NEW_NAME_TAG).performTextInput("Main")
		compose.onNodeWithTag(TIME_LINE_NEW_CONFIRM_TAG).performClick()
		compose.waitForIdle()

		assertEquals(listOf("Main"), component.created)
		compose.onNodeWithTag(TIME_LINE_NEW_NAME_TAG).assertIsDisplayed()
	}

	@Test
	fun `Renaming from the list closes once the timeline carries the new name`() {
		val component = threeTimelines()
		show(component)

		compose.onNodeWithTag(timelineSettingsRenameTag("scratch")).performClick()
		compose.waitForIdle()
		compose.onNodeWithTag(TIME_LINE_RENAME_NAME_TAG).performTextClearance()
		compose.onNodeWithTag(TIME_LINE_RENAME_NAME_TAG).performTextInput("Notes")
		compose.onNodeWithTag(TIME_LINE_RENAME_CONFIRM_TAG).performClick()
		compose.waitForIdle()

		assertEquals(listOf("scratch" to "Notes"), component.renamed)
		compose.onNodeWithTag(TIME_LINE_RENAME_NAME_TAG).assertDoesNotExist()
	}

	@Test
	fun `With several timelines a mode switch names the timeline and counts only its entries`() {
		show(threeTimelines(editing = backstory))

		compose.onNodeWithText("MANUAL").performClick()
		compose.waitForIdle()

		compose.onNodeWithText("Switch “Before the evening” to manual?").assertIsDisplayed()
		compose.onNodeWithTag(TIMELINE_MODE_DIALOG_COUNT_TAG).assertTextEquals("13 ENTRIES")
	}

	@Test
	fun `With several timelines a calendar warning names the timeline`() {
		show(
			FakeSettings(
				TimelineSettings.State(
					timelines = listOf(main, backstory, scratch),
					editingId = backstory.def.id,
					def = backstory.def,
					pendingCalendar = CalendarDef(CalendarPreset.STORY),
					datesLostOnPresetSwitch = 3,
					presetSwitchTimeline = backstory.def,
				)
			)
		)

		compose.onNodeWithText("Some dates on “Before the evening” do not fit", substring = true, ignoreCase = true)
			.assertIsDisplayed()
		compose.onNodeWithText("3 entries keep their date as a label").assertIsDisplayed()
	}
}
