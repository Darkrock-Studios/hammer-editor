package com.darkrockstudios.apps.hammer.common.preview.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings.ModeSwitchPrompt
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.entryUnits
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.preview.timeline.previewThreeTimelines
import com.darkrockstudios.apps.hammer.common.projecthome.TimelineSettingsUi

private val storyCalendar = CalendarDef(CalendarPreset.STORY, epochName = "The Landing")
private val previewLanes = listOf("Keeper", "Village", "The Sea", "Maren")

@Preview
@Composable
fun TimelineSettingsUiManualPreview() {
	SettingsPreview(TimelineSettings.State(def = TimelineDef.DEFAULT, lanes = previewLanes))
}

@Preview
@Composable
fun TimelineSettingsUiDatedStoryPreview() {
	SettingsPreview(
		TimelineSettings.State(
			def = TimelineDef.DEFAULT.copy(mode = TimelineMode.DATED, calendar = storyCalendar, laneOrder = previewLanes),
			lanes = previewLanes,
		)
	)
}

@Preview
@Composable
fun TimelineSettingsUiPresetWarningPreview() {
	SettingsPreview(
		TimelineSettings.State(
			def = TimelineDef.DEFAULT.copy(mode = TimelineMode.DATED, calendar = storyCalendar),
			lanes = previewLanes,
			pendingCalendar = CalendarDef(CalendarPreset.GREGORIAN),
			datesLostOnPresetSwitch = 3,
		)
	)
}

@Preview
@Composable
fun TimelineSettingsUiModeDialogPreview() {
	val units = entryUnits(storyCalendar)
	SettingsPreview(
		TimelineSettings.State(
			def = TimelineDef.DEFAULT,
			lanes = previewLanes,
			pendingModeSwitch = ModeSwitchPrompt.ToDated(units, units.first().name, storyCalendar),
		)
	)
}

/** Main, a dated backstory being edited, and a scratch timeline. */
@Preview
@Composable
fun TimelineSettingsUiThreeTimelinesPreview() {
	val editing = previewThreeTimelines[1]
	SettingsPreview(
		TimelineSettings.State(
			timelines = previewThreeTimelines,
			editingId = editing.def.id,
			def = editing.def,
			lanes = previewLanes,
		)
	)
}

/** Switching the backstory to manual names it and counts only its entries. */
@Preview
@Composable
fun TimelineSettingsUiNamedModeDialogPreview() {
	val editing = previewThreeTimelines[1]
	SettingsPreview(
		TimelineSettings.State(
			timelines = previewThreeTimelines,
			editingId = editing.def.id,
			def = editing.def,
			lanes = previewLanes,
			pendingModeSwitch = ModeSwitchPrompt.ToManual(timeline = editing.def, eventCount = editing.eventCount),
		)
	)
}

@Composable
private fun SettingsPreview(state: TimelineSettings.State) {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview) {
			Box(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.background)
					.width(720.dp)
					.padding(24.dp),
			) {
				TimelineSettingsUi(component = PreviewTimelineSettings(state), section = 6)
			}
		}
	}
}

internal class PreviewTimelineSettings(initial: TimelineSettings.State) : TimelineSettings {
	private val _state = MutableValue(initial)
	override val state: Value<TimelineSettings.State> = _state

	override fun requestMode(mode: TimelineMode) {
		val calendar = _state.value.def.calendar ?: CalendarDef()
		val units = entryUnits(calendar)
		val prompt = when (mode) {
			TimelineMode.DATED -> ModeSwitchPrompt.ToDated(units, units.first().name, calendar)
			TimelineMode.MANUAL -> ModeSwitchPrompt.ToManual()
		}
		if (mode != _state.value.def.mode) _state.value = _state.value.copy(pendingModeSwitch = prompt)
	}

	override fun setSpacingUnit(unit: String) {
		val prompt = _state.value.pendingModeSwitch as? ModeSwitchPrompt.ToDated ?: return
		_state.value = _state.value.copy(pendingModeSwitch = prompt.copy(selectedUnit = unit))
	}

	override fun confirmModeSwitch() {
		val dated = _state.value.pendingModeSwitch is ModeSwitchPrompt.ToDated
		val def = _state.value.def
		_state.value = _state.value.copy(
			def = def.copy(
				mode = if (dated) TimelineMode.DATED else TimelineMode.MANUAL,
				calendar = def.calendar ?: CalendarDef(),
			),
			pendingModeSwitch = null,
		)
	}

	override fun cancelModeSwitch() {
		_state.value = _state.value.copy(pendingModeSwitch = null)
	}

	override fun setPreset(preset: CalendarPreset) {
		_state.value = _state.value.copy(def = _state.value.def.copy(calendar = CalendarDef(preset)))
	}

	override fun setCoarsestUnit(unit: String?) {}
	override fun confirmPresetSwitch() {
		_state.value = _state.value.copy(pendingCalendar = null, datesLostOnPresetSwitch = null)
	}

	override fun cancelPresetSwitch() {
		_state.value = _state.value.copy(pendingCalendar = null, datesLostOnPresetSwitch = null)
	}

	override fun setEpochName(name: String) {}
	override fun setLaneOrder(order: List<String>) {
		_state.value = _state.value.copy(lanes = order)
	}

	override fun selectTimeline(id: String) {
		val def = _state.value.timelines.firstOrNull { it.def.id == id }?.def ?: return
		_state.value = _state.value.copy(editingId = id, def = def)
	}

	override fun createTimeline(name: String, mode: TimelineMode, preset: CalendarPreset) {}
	override fun renameTimeline(id: String, name: String) {}
	override fun requestDelete(id: String) {
		_state.value = _state.value.copy(pendingDelete = _state.value.timelines.firstOrNull { it.def.id == id })
	}

	override fun confirmDelete() {
		_state.value = _state.value.copy(pendingDelete = null)
	}

	override fun cancelDelete() {
		_state.value = _state.value.copy(pendingDelete = null)
	}
}
