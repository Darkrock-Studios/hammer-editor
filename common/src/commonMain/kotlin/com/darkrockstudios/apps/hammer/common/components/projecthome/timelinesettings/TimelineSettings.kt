package com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings

import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.CalendarUnit
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineSummary

/** The project's timelines, and the mode, calendar and lane order of the one being edited. */
interface TimelineSettings {
	val state: Value<State>

	/** Edits timeline [id]; the settings below then apply to it. */
	fun selectTimeline(id: String)

	/** Creates a timeline, DATED ones on [preset], and edits it. */
	fun createTimeline(name: String, mode: TimelineMode, preset: CalendarPreset)
	fun renameTimeline(id: String, name: String)

	/** Asks before deleting timeline [id] and its events; main cannot be deleted. */
	fun requestDelete(id: String)
	fun confirmDelete()
	fun cancelDelete()

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
		/** The live timelines, main first. */
		val timelines: List<TimelineSummary> = emptyList(),
		val editingId: String = TimelineDef.MAIN_ID,
		/** The timeline being edited. */
		val def: TimelineDef = TimelineDef.DEFAULT,
		/** The lane order, then any other lane in use. */
		val lanes: List<String> = emptyList(),
		val pendingModeSwitch: ModeSwitchPrompt? = null,
		/** A calendar change waiting on confirmation because it would take dates away. */
		val pendingCalendar: CalendarDef? = null,
		/** How many entries [pendingCalendar] would take the start from; they keep it as a label. */
		val datesLostOnPresetSwitch: Int? = null,
		/** The timeline [pendingCalendar] is for. */
		val presetSwitchTimeline: TimelineDef? = null,
		/** A timeline waiting on confirmation to be deleted with its events. */
		val pendingDelete: TimelineSummary? = null,
		val error: TimeLineEventError? = null,
	)

	/** A mode switch for [timeline], which holds [eventCount] entries. */
	sealed interface ModeSwitchPrompt {
		val timeline: TimelineDef
		val eventCount: Int

		/** [units] are the calendar's entry units; [selectedUnit] spaces the undated entries. */
		data class ToDated(
			val units: List<CalendarUnit>,
			val selectedUnit: String,
			val calendar: CalendarDef,
			override val timeline: TimelineDef = TimelineDef.DEFAULT,
			override val eventCount: Int = 0,
		) : ModeSwitchPrompt

		data class ToManual(
			override val timeline: TimelineDef = TimelineDef.DEFAULT,
			override val eventCount: Int = 0,
		) : ModeSwitchPrompt
	}
}
