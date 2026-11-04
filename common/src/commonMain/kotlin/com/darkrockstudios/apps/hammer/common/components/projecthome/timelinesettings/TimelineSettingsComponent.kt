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
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.ProjectComponentBase
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings.ModeSwitchPrompt
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSwitcher
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectInject
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.EvenSpacing
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.StrResStoryDateWords
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineDefResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.entryUnits
import com.darkrockstudios.apps.hammer.common.dependencyinjection.APP_SCOPE
import com.darkrockstudios.apps.hammer.common.util.StrRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
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

	/** An epoch typed for [timelineId], waiting on the debounce. */
	private data class PendingEpoch(val timelineId: String, val name: String)

	@Volatile
	private var pendingEpoch: PendingEpoch? = null
	private var epochWrite: Job? = null

	private val _state = MutableValue(TimelineSettings.State())
	override val state: Value<TimelineSettings.State> = _state

	private val editingId = MutableStateFlow(_state.value.editingId)

	@OptIn(ExperimentalCoroutinesApi::class)
	override fun onCreate() {
		super.onCreate()
		scope.launch {
			editingId.flatMapLatest { timelineService.timelineFlow(it) }.collect { view ->
				val lanes = timelineService.lanes(view.def.id)
				withContext(dispatcherMain) {
					_state.getAndUpdate { if (it.editingId != view.def.id) it else it.copy(def = view.def, lanes = lanes) }
				}
			}
		}
		scope.launch {
			timelineService.summaries.collect { timelines ->
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(timelines = timelines) }
					// The edited timeline was deleted, here or on another device.
					if (timelines.none { it.def.id == _state.value.editingId }) selectTimeline(TimelineDef.MAIN_ID)
				}
			}
		}
	}

	override fun selectTimeline(id: String) {
		val timelines = _state.value.timelines
		// Before the list loads, the summaries collector falls back to main instead.
		edit(id.takeIf { timelines.isEmpty() || timelines.any { it.def.id == id } } ?: TimelineDef.MAIN_ID)
	}

	/** Edits [target] without checking it is live, for a timeline the summaries may not list yet. */
	private fun edit(target: String) {
		if (_state.value.editingId == target) return
		_state.getAndUpdate { current ->
			current.copy(
				editingId = target,
				def = current.timelines.firstOrNull { it.def.id == target }?.def ?: current.def,
				pendingModeSwitch = null,
				pendingCalendar = null,
				datesLostOnPresetSwitch = null,
				presetSwitchTimeline = null,
				error = null,
			)
		}
		editingId.value = target
	}

	override fun requestCreate() {
		setSwitcher(TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Create()))
	}

	override fun createTimeline(name: String, mode: TimelineMode, preset: CalendarPreset) {
		val draft = TimelineSwitcher.Dialog.Create(name, mode, preset)
		setSwitcher(TimelineSwitcher(dialog = draft))
		scope.launch {
			val calendar = if (mode == TimelineMode.DATED) CalendarDef(preset) else null
			val result = timelineService.createTimeline(name, mode, calendar)
			withContext(dispatcherMain) {
				when (result) {
					is TimelineDefResult.Ok -> {
						closeIfOpen(draft)
						edit(result.def.id)
					}

					is TimelineDefResult.Invalid -> refused(draft, result)
				}
			}
		}
	}

	override fun requestRename(id: String) {
		val def = _state.value.timelines.firstOrNull { it.def.id == id }?.def ?: return
		setSwitcher(TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Rename(def.id, def.name)))
	}

	override fun renameTimeline(name: String) {
		val open = _state.value.switcher.dialog as? TimelineSwitcher.Dialog.Rename ?: return
		val draft = open.copy(name = name)
		setSwitcher(TimelineSwitcher(dialog = draft))
		scope.launch {
			val result = timelineService.renameTimeline(draft.timelineId, name)
			withContext(dispatcherMain) {
				when (result) {
					is TimelineDefResult.Ok -> closeIfOpen(draft)
					is TimelineDefResult.Invalid -> refused(draft, result)
				}
			}
		}
	}

	override fun requestDelete(id: String) {
		if (id == TimelineDef.MAIN_ID) return
		val summary = _state.value.timelines.firstOrNull { it.def.id == id } ?: return
		setSwitcher(
			TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Delete(summary.def.id, summary.def.name, summary.eventCount))
		)
	}

	override fun confirmDelete() {
		val open = _state.value.switcher.dialog as? TimelineSwitcher.Dialog.Delete ?: return
		setSwitcher(TimelineSwitcher(dialog = open))
		scope.launch {
			val result = timelineService.deleteTimeline(open.timelineId)
			withContext(dispatcherMain) {
				when (result) {
					is TimelineDefResult.Ok -> closeIfOpen(open)
					is TimelineDefResult.Invalid -> refused(open, result)
				}
			}
		}
	}

	override fun dismissDialog() {
		setSwitcher(TimelineSwitcher())
	}

	private fun setSwitcher(switcher: TimelineSwitcher) {
		_state.getAndUpdate { it.copy(switcher = switcher) }
	}

	// A dialog closed or replaced while its request ran is left as it is.
	private fun closeIfOpen(dialog: TimelineSwitcher.Dialog) {
		_state.getAndUpdate { if (it.switcher.dialog == dialog) it.copy(switcher = TimelineSwitcher()) else it }
	}

	private fun refused(dialog: TimelineSwitcher.Dialog, result: TimelineDefResult.Invalid) {
		_state.getAndUpdate {
			if (it.switcher.dialog == dialog) it.copy(switcher = it.switcher.copy(error = result.error)) else it
		}
	}

	override fun requestMode(mode: TimelineMode) {
		val current = _state.value
		val def = current.def
		if (def.mode == mode) return
		val count = current.timelines.firstOrNull { it.def.id == def.id }?.eventCount ?: 0
		val prompt = when (mode) {
			TimelineMode.DATED -> {
				val calendar = def.calendar ?: CalendarDef(CalendarPreset.STORY)
				val units = entryUnits(calendar)
				ModeSwitchPrompt.ToDated(
					units = units,
					selectedUnit = spacingUnit(calendar, units),
					calendar = calendar,
					timeline = def,
					eventCount = count,
				)
			}

			TimelineMode.MANUAL -> ModeSwitchPrompt.ToManual(timeline = def, eventCount = count)
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
					prompt.timeline.id,
				)

				is ModeSwitchPrompt.ToManual -> timelineService.switchToManual(StrResStoryDateWords(strRes), prompt.timeline.id)
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
		val def = _state.value.def
		scope.launch {
			val lost = timelineService.countDatesLostBySwitch(calendar, def.id)
			if (lost == 0) {
				apply(timelineService.switchPreset(calendar, StrResStoryDateWords(strRes), def.id))
				withContext(dispatcherMain) {
					_state.getAndUpdate { it.copy(pendingCalendar = null, datesLostOnPresetSwitch = null, presetSwitchTimeline = null) }
				}
			} else {
				withContext(dispatcherMain) {
					_state.getAndUpdate {
						it.copy(pendingCalendar = calendar, datesLostOnPresetSwitch = lost, presetSwitchTimeline = def, error = null)
					}
				}
			}
		}
	}

	override fun confirmPresetSwitch() {
		val calendar = _state.value.pendingCalendar ?: return
		val timeline = _state.value.presetSwitchTimeline?.id ?: _state.value.editingId
		cancelPresetSwitch()
		write { timelineService.switchPreset(calendar, StrResStoryDateWords(strRes), timeline) }
	}

	override fun cancelPresetSwitch() {
		_state.getAndUpdate { it.copy(pendingCalendar = null, datesLostOnPresetSwitch = null, presetSwitchTimeline = null) }
	}

	override fun setEpochName(name: String) {
		val next = PendingEpoch(_state.value.editingId, name)
		// An epoch still waiting for another timeline is written now rather than dropped.
		pendingEpoch?.takeIf { it.timelineId != next.timelineId }?.let { other ->
			scope.launch { flushEpoch(other)?.let { apply(it) } }
		}
		pendingEpoch = next
		epochWrite?.cancel()
		epochWrite = scope.launch {
			delay(EPOCH_DEBOUNCE)
			flushEpoch()?.let { apply(it) }
		}
	}

	/** Writes [pending], by default the epoch waiting on the debounce, to the timeline it was typed for. */
	private suspend fun flushEpoch(pending: PendingEpoch? = pendingEpoch): TimelineResult? {
		pending ?: return null
		val epoch = pending.name.trim().ifEmpty { null }
		val result = timelineService.updateDefinition(pending.timelineId) { def ->
			val calendar = def.calendar ?: CalendarDef()
			if (calendar.preset != CalendarPreset.STORY) def else def.copy(calendar = calendar.copy(epochName = epoch))
		}
		if (pendingEpoch == pending) pendingEpoch = null
		return result
	}

	override fun onDestroy() {
		super.onDestroy()
		if (pendingEpoch != null) appScope.launch { flushEpoch() }
	}

	override fun setLaneOrder(order: List<String>) {
		val timeline = _state.value.editingId
		write { timelineService.updateDefinition(timeline) { it.copy(laneOrder = order) } }
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
