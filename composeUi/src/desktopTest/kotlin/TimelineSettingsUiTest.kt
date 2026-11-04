import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
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
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.entryUnits
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.projecthome.TIMELINE_MODE_DIALOG_CONFIRM_TAG
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

		override fun requestMode(mode: TimelineMode) {
			if (mode == _state.value.def.mode) return
			val prompt = when (mode) {
				TimelineMode.DATED -> {
					val calendar = _state.value.def.calendar ?: CalendarDef()
					val units = entryUnits(calendar)
					ModeSwitchPrompt.ToDated(units, "day", calendar)
				}

				TimelineMode.MANUAL -> ModeSwitchPrompt.ToManual()
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
		override fun selectTimeline(id: String) {}
		override fun createTimeline(name: String, mode: TimelineMode, preset: CalendarPreset) {}
		override fun renameTimeline(id: String, name: String) {}
		override fun requestDelete(id: String) {}
		override fun confirmDelete() {}
		override fun cancelDelete() {}
	}

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
}
