package com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings.ModeSwitchPrompt
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.EvenSpacing
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.entryUnits
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimelineSettingsComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
) : ProjectComponentBase(projectDef, componentContext), TimelineSettings {

	private val timelineService: TimelineService by projectInject()

	private val _state = MutableValue(TimelineSettings.State())
	override val state: Value<TimelineSettings.State> = _state

	override fun onCreate() {
		super.onCreate()
		scope.launch {
			timelineService.timelineFlow.collect { view ->
				val lanes = timelineService.lanes()
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(def = view.def, lanes = lanes, isLoaded = true) }
				}
			}
		}
	}

	override fun requestMode(mode: TimelineMode) {
		val def = _state.value.def
		if (def.mode == mode) return
		val prompt = when (mode) {
			TimelineMode.DATED -> {
				val calendar = def.calendar ?: CalendarDef(CalendarPreset.STORY)
				val units = entryUnits(calendar)
				ModeSwitchPrompt.ToDated(units = units, selectedUnit = units.first().name, calendar = calendar)
			}

			TimelineMode.MANUAL -> ModeSwitchPrompt.ToManual
		}
		_state.getAndUpdate { it.copy(pendingModeSwitch = prompt, error = null) }
	}

	override fun setSpacingUnit(unit: String) {
		_state.getAndUpdate { current ->
			val prompt = current.pendingModeSwitch as? ModeSwitchPrompt.ToDated ?: return@getAndUpdate current
			if (prompt.units.none { it.name == unit }) return@getAndUpdate current
			current.copy(pendingModeSwitch = prompt.copy(selectedUnit = unit))
		}
	}

	override fun confirmModeSwitch() {
		val prompt = _state.value.pendingModeSwitch ?: return
		_state.getAndUpdate { it.copy(pendingModeSwitch = null) }
		write {
			when (prompt) {
				is ModeSwitchPrompt.ToDated -> timelineService.switchToDated(
					prompt.calendar,
					EvenSpacing(unit = prompt.selectedUnit, step = 1, origin = spacingOrigin(prompt.calendar)),
				)

				ModeSwitchPrompt.ToManual -> timelineService.switchToManual()
			}
		}
	}

	override fun cancelModeSwitch() {
		_state.getAndUpdate { it.copy(pendingModeSwitch = null) }
	}

	override fun setPreset(preset: CalendarPreset) {
		val current = _state.value.def.calendar
		if (current?.preset == preset) return
		requestCalendar(CalendarDef(preset))
	}

	override fun setCoarsestUnit(unit: String?) {
		val current = _state.value.def.calendar ?: CalendarDef()
		if (current.preset != CalendarPreset.STORY) return
		val coarsest = unit?.takeIf { it != StoryCalendar.units(CalendarPreset.STORY).first().name }
		if (current.coarsestUnit == coarsest) return
		requestCalendar(current.copy(coarsestUnit = coarsest))
	}

	private fun requestCalendar(calendar: CalendarDef) {
		scope.launch {
			val lost = timelineService.countDatesLostBySwitch(calendar)
			if (lost == 0) {
				apply(timelineService.switchPreset(calendar))
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(pendingCalendar = null, datesLostOnPresetSwitch = null) }
				}
			} else {
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(pendingCalendar = calendar, datesLostOnPresetSwitch = lost, error = null) }
				}
			}
		}
	}

	override fun confirmPresetSwitch() {
		val calendar = _state.value.pendingCalendar ?: return
		_state.getAndUpdate { it.copy(pendingCalendar = null, datesLostOnPresetSwitch = null) }
		write { timelineService.switchPreset(calendar) }
	}

	override fun cancelPresetSwitch() {
		_state.getAndUpdate { it.copy(pendingCalendar = null, datesLostOnPresetSwitch = null) }
	}

	override fun setEpochName(name: String) {
		val epoch = name.trim().ifEmpty { null }
		write {
			timelineService.updateDefinition { def ->
				val calendar = def.calendar ?: CalendarDef()
				if (calendar.preset != CalendarPreset.STORY) def else def.copy(calendar = calendar.copy(epochName = epoch))
			}
		}
	}

	override fun setLaneOrder(order: List<String>) {
		write { timelineService.updateDefinition { it.copy(laneOrder = order) } }
	}

	private fun write(block: suspend () -> TimelineResult) {
		scope.launch { apply(block()) }
	}

	private suspend fun apply(result: TimelineResult) {
		val error = (result as? TimelineResult.Invalid)?.error
		withContext(dispatcherMain) {
			_state.getAndUpdate { it.copy(error = error) }
		}
	}

	private companion object {
		/** Day zero of a STORY calendar's entry units, or 1 January of year 1. */
		fun spacingOrigin(calendar: CalendarDef): StoryDate = when (calendar.preset) {
			CalendarPreset.STORY -> StoryDate(mapOf(entryUnits(calendar).first().name to 0L))
			CalendarPreset.GREGORIAN -> StoryDate(
				linkedMapOf(StoryCalendar.YEAR to 1L, StoryCalendar.MONTH to 1L, StoryCalendar.DAY to 1L)
			)
		}
	}
}
