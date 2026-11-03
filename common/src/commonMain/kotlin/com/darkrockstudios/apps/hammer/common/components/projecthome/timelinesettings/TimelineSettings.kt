package com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.CalendarUnit
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError

/** The project's timeline settings: mode, calendar and lane order. */
interface TimelineSettings {
	val state: Value<State>

	/** Opens the prompt for switching to [mode]; nothing happens when the timeline is in it already. */
	fun requestMode(mode: TimelineMode)

	/** The unit "space evenly" steps by, for a pending switch to DATED. */
	fun setSpacingUnit(unit: String)
	fun confirmModeSwitch()
	fun cancelModeSwitch()

	/**
	 * Moves the calendar to [preset]. When dates would not fit the new calendar, the switch waits for
	 * [confirmPresetSwitch]; otherwise it applies at once.
	 */
	fun setPreset(preset: CalendarPreset)

	/** STORY only: the coarsest unit the story is told in; null for the year. Confirmed like a preset switch. */
	fun setCoarsestUnit(unit: String?)
	fun confirmPresetSwitch()
	fun cancelPresetSwitch()

	/** STORY only; blank clears it. Written once calls pause, so a text field can call it per keystroke. */
	fun setEpochName(name: String)

	/** Every lane, in the order to show them; a lane's position is its colour index. */
	fun setLaneOrder(order: List<String>)

	data class State(
		val def: TimelineDef = TimelineDef.DEFAULT,
		/** The lane order, then any other lane in use. */
		val lanes: List<String> = emptyList(),
		val pendingModeSwitch: ModeSwitchPrompt? = null,
		/** A calendar change waiting on confirmation because it would take dates away. */
		val pendingCalendar: CalendarDef? = null,
		/** How many entries [pendingCalendar] would take the start from; they keep it as a label. */
		val datesLostOnPresetSwitch: Int? = null,
		val error: TimeLineEventError? = null,
	)

	sealed interface ModeSwitchPrompt {
		/** [units] are the calendar's entry units; [selectedUnit] spaces the undated entries. */
		data class ToDated(
			val units: List<CalendarUnit>,
			val selectedUnit: String,
			val calendar: CalendarDef,
		) : ModeSwitchPrompt

		data object ToManual : ModeSwitchPrompt
	}
}
