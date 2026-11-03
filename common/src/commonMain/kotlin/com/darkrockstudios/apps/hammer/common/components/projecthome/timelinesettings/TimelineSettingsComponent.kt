package com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.CalendarUnit
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings.ModeSwitchPrompt
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.EvenSpacing
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.StrResStoryDateWords
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.entryUnits
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

class TimelineSettingsComponent(
	componentContext: ComponentContext,
	projectDef: ProjectDef,
) : ProjectComponentBase(projectDef, componentContext), TimelineSettings {

	private val timelineService: TimelineService by projectInject()

	private val appScope: CoroutineScope by inject(named(APP_SCOPE))
	private val strRes: StrRes by inject()

	@Volatile
	private var pendingEpoch: String? = null
	private var epochWrite: Job? = null

	private val _state = MutableValue(TimelineSettings.State())
	override val state: Value<TimelineSettings.State> = _state

	override fun onCreate() {
		super.onCreate()
		scope.launch {
			timelineService.timelineFlow.collect { view ->
				val lanes = timelineService.lanes()
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(def = view.def, lanes = lanes) }
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
				ModeSwitchPrompt.ToDated(units = units, selectedUnit = spacingUnit(calendar, units), calendar = calendar)
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

				ModeSwitchPrompt.ToManual -> timelineService.switchToManual(StrResStoryDateWords(strRes))
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
				apply(timelineService.switchPreset(calendar, StrResStoryDateWords(strRes)))
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
		write { timelineService.switchPreset(calendar, StrResStoryDateWords(strRes)) }
	}

	override fun cancelPresetSwitch() {
		_state.getAndUpdate { it.copy(pendingCalendar = null, datesLostOnPresetSwitch = null) }
	}

	override fun setEpochName(name: String) {
		pendingEpoch = name
		epochWrite?.cancel()
		epochWrite = scope.launch {
			delay(EPOCH_DEBOUNCE)
			flushEpoch()?.let { apply(it) }
		}
	}

	/** Writes the epoch waiting on the debounce, if any. */
	private suspend fun flushEpoch(): TimelineResult? {
		val name = pendingEpoch ?: return null
		val epoch = name.trim().ifEmpty { null }
		val result = timelineService.updateDefinition { def ->
			val calendar = def.calendar ?: CalendarDef()
			if (calendar.preset != CalendarPreset.STORY) def else def.copy(calendar = calendar.copy(epochName = epoch))
		}
		if (pendingEpoch == name) pendingEpoch = null
		return result
	}

	override fun onDestroy() {
		super.onDestroy()
		if (pendingEpoch != null) appScope.launch { flushEpoch() }
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
		/** The epoch field writes on every keystroke; this keeps that to one write per pause. */
		val EPOCH_DEBOUNCE = 600.milliseconds

		/** The story's own coarsest unit when it names one, else a day, as long as the calendar offers it. */
		fun spacingUnit(calendar: CalendarDef, units: List<CalendarUnit>): String {
			val preferred = calendar.coarsestUnit?.takeIf { calendar.preset == CalendarPreset.STORY } ?: StoryCalendar.DAY
			return units.firstOrNull { it.name == preferred }?.name ?: units.first().name
		}

		/** Day zero of a STORY calendar's entry units, or 1 January of year 1. */
		fun spacingOrigin(calendar: CalendarDef): StoryDate = when (calendar.preset) {
			CalendarPreset.STORY -> StoryDate(mapOf(entryUnits(calendar).first().name to 0L))
			CalendarPreset.GREGORIAN -> StoryDate(
				linkedMapOf(StoryCalendar.YEAR to 1L, StoryCalendar.MONTH to 1L, StoryCalendar.DAY to 1L)
			)
		}
	}
}
