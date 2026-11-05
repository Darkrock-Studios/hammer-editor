package com.darkrockstudios.apps.hammer.common.components.timeline

import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineDefResult
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineService
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * The create, rename and delete dialogs for whole timelines, as the timeline screen and the project
 * settings both show them. Calls are made on [mainContext]; a request that finishes after its dialog
 * was closed or replaced leaves the newer dialog alone.
 */
internal class TimelineDefDialogs(
	private val timelineService: TimelineService,
	private val scope: CoroutineScope,
	private val mainContext: CoroutineContext,
	private val onCreated: (TimelineDef) -> Unit = {},
	private val onDeleted: (TimelineDef) -> Unit = {},
) {
	private val _state = MutableValue(TimelineSwitcher())
	val state: Value<TimelineSwitcher> = _state

	fun requestCreate() {
		_state.value = TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Create())
	}

	fun create(name: String, mode: TimelineMode, preset: CalendarPreset) {
		val draft = TimelineSwitcher.Dialog.Create(name, mode, preset)
		_state.value = TimelineSwitcher(dialog = draft)
		scope.launch {
			val calendar = if (mode == TimelineMode.DATED) CalendarDef(preset) else null
			val result = timelineService.createTimeline(name, mode, calendar)
			withContext(mainContext) {
				finish(draft, result)
				if (result is TimelineDefResult.Ok) onCreated(result.def)
			}
		}
	}

	fun requestRename(def: TimelineDef) {
		_state.value = TimelineSwitcher(dialog = TimelineSwitcher.Dialog.Rename(def.id, def.name))
	}

	fun rename(name: String) {
		val open = _state.value.dialog as? TimelineSwitcher.Dialog.Rename ?: return
		val draft = open.copy(name = name)
		_state.value = TimelineSwitcher(dialog = draft)
		scope.launch {
			val result = timelineService.renameTimeline(draft.timelineId, name)
			withContext(mainContext) { finish(draft, result) }
		}
	}

	/** Main cannot be deleted, so asking for it does nothing. */
	fun requestDelete(summary: TimelineSummary) {
		if (summary.def.id == TimelineDef.MAIN_ID) return
		_state.value = TimelineSwitcher(
			dialog = TimelineSwitcher.Dialog.Delete(summary.def.id, summary.def.name, summary.eventCount)
		)
	}

	fun confirmDelete() {
		val open = _state.value.dialog as? TimelineSwitcher.Dialog.Delete ?: return
		_state.value = TimelineSwitcher(dialog = open)
		scope.launch {
			val result = timelineService.deleteTimeline(open.timelineId)
			withContext(mainContext) {
				finish(open, result)
				if (result is TimelineDefResult.Ok) onDeleted(result.def)
			}
		}
	}

	fun dismiss() {
		_state.value = TimelineSwitcher()
	}

	private fun finish(dialog: TimelineSwitcher.Dialog, result: TimelineDefResult) {
		_state.getAndUpdate { current ->
			when {
				current.dialog != dialog -> current
				result is TimelineDefResult.Invalid -> current.copy(error = result.error)
				else -> TimelineSwitcher()
			}
		}
	}
}
