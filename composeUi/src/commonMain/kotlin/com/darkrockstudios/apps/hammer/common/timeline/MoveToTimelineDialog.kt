package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.StoryDate
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDropdown
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.timeline_move_confirm
import com.darkrockstudios.apps.hammer.timeline_move_needs_date
import com.darkrockstudios.apps.hammer.timeline_move_target_label
import com.darkrockstudios.apps.hammer.timeline_move_title
import com.darkrockstudios.apps.hammer.timeline_switcher_cancel
import com.darkrockstudios.apps.hammer.timeline_switcher_close
import kotlinx.coroutines.launch

const val MOVE_TO_TIMELINE_TARGET_TAG = "Move To Timeline Target"
const val MOVE_TO_TIMELINE_NEEDS_DATE_TAG = "Move To Timeline Needs Date"
const val MOVE_TO_TIMELINE_START_TAG = "Move To Timeline Start"
const val MOVE_TO_TIMELINE_END_TAG = "Move To Timeline End"
const val MOVE_TO_TIMELINE_ADD_END_TAG = "Move To Timeline Add End"
const val MOVE_TO_TIMELINE_ERROR_TAG = "Move To Timeline Error"
const val MOVE_TO_TIMELINE_CONFIRM_TAG = "Move To Timeline Confirm"

/**
 * Moves an event to one of [targets]. The event's own dates are tried first; when a DATED target
 * refuses them, the dialog asks for a date on that target's calendar and moves with it. [onMove]
 * returns why a move was refused, or [TimeLineEventError.NONE] once the event has moved.
 */
@Composable
fun MoveToTimelineDialog(
	visible: Boolean,
	targets: List<TimelineDef>,
	onMove: suspend (target: String, start: StoryDate?, end: StoryDate?) -> TimeLineEventError,
	onDismiss: () -> Unit,
) {
	AnimatedDialog(visible = visible, onCloseRequest = onDismiss) {
		val draft = remember { MoveToTimelineDraft(targets.firstOrNull()?.id) }
		HdHairlineDialogShell(
			title = Res.string.timeline_move_title.get(),
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.timeline_switcher_close.get(),
		) {
			MoveToTimelineFields(targets = targets, draft = draft, onMove = onMove, onCancel = { requestDismiss() })
		}
	}
}

/** What the move dialog has chosen so far. */
@Stable
internal class MoveToTimelineDraft(target: String?, dateNeeded: Boolean = false) {
	var target by mutableStateOf(target)
		private set

	/** Set once the target refused the event's own dates; the move then carries [start] and [end]. */
	var dateNeeded by mutableStateOf(dateNeeded)
	var start by mutableStateOf<StoryDate?>(null)
	var end by mutableStateOf<StoryDate?>(null)
	var error by mutableStateOf<TimeLineEventError?>(null)
	var busy by mutableStateOf(false)

	/**
	 * The targets as they were when the move was sent. Moving changes which timelines the event can
	 * go to, and the dialog keeps showing the one it went to while it closes.
	 */
	var sentTargets by mutableStateOf<List<TimelineDef>?>(null)

	fun pick(id: String) {
		if (id == target) return
		target = id
		dateNeeded = false
		start = null
		end = null
		error = null
	}
}

@Composable
internal fun MoveToTimelineFields(
	targets: List<TimelineDef>,
	draft: MoveToTimelineDraft,
	onMove: suspend (target: String, start: StoryDate?, end: StoryDate?) -> TimeLineEventError,
	onCancel: () -> Unit,
) {
	val scope = rememberCoroutineScope()
	val shown = draft.sentTargets ?: targets
	val selected = shown.firstOrNull { it.id == draft.target } ?: shown.firstOrNull()

	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
		if (selected != null) {
			HdHairlineDropdown(
				options = shown,
				selected = selected,
				onSelect = { draft.pick(it.id) },
				label = { it.name },
				title = Res.string.timeline_move_target_label.get(),
				enabled = !draft.busy,
				modifier = Modifier.testTag(MOVE_TO_TIMELINE_TARGET_TAG),
			)
		}

		if (selected != null && draft.dateNeeded) {
			Text(
				text = Res.string.timeline_move_needs_date.get(selected.name),
				style = MaterialTheme.typography.bodyMedium,
				modifier = Modifier.testTag(MOVE_TO_TIMELINE_NEEDS_DATE_TAG),
			)
			key(selected.id) {
				DateRows(
					calendar = selected.calendar ?: CalendarDef(),
					start = draft.start,
					end = draft.end,
					onStartChange = { draft.start = it },
					onEndChange = { draft.end = it },
					startTag = MOVE_TO_TIMELINE_START_TAG,
					endTag = MOVE_TO_TIMELINE_END_TAG,
					addEndTag = MOVE_TO_TIMELINE_ADD_END_TAG,
				)
			}
		}

		errorText(draft.error)?.let {
			HdMonoLabel(
				text = it,
				color = MaterialTheme.colorScheme.error,
				modifier = Modifier.testTag(MOVE_TO_TIMELINE_ERROR_TAG),
			)
		}

		Row(horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
			Spacer(modifier = Modifier.weight(1f))
			HdHairlineButton(label = Res.string.timeline_switcher_cancel.get(), onClick = onCancel)
			HdHairlineButton(
				label = Res.string.timeline_move_confirm.get(),
				onClick = {
					val target = selected ?: return@HdHairlineButton
					scope.launch { draft.send(target.id, targets, onMove) }
				},
				emphasised = true,
				enabled = selected != null && !draft.busy && (!draft.dateNeeded || draft.start != null),
				modifier = Modifier.testTag(MOVE_TO_TIMELINE_CONFIRM_TAG),
			)
		}
	}
}

private suspend fun MoveToTimelineDraft.send(
	target: String,
	targets: List<TimelineDef>,
	onMove: suspend (target: String, start: StoryDate?, end: StoryDate?) -> TimeLineEventError,
) {
	val withDates = dateNeeded
	busy = true
	sentTargets = targets
	val result = if (withDates) onMove(target, start, end) else onMove(target, null, null)
	busy = false
	if (result == TimeLineEventError.NONE) return
	sentTargets = null
	// A refusal for a target picked away from in the meantime is stale.
	if (this.target != target) return
	val dateRefused = result == TimeLineEventError.DATE_REQUIRED || result == TimeLineEventError.DATE_INVALID
	if (dateRefused && !withDates) {
		dateNeeded = true
		error = null
	} else {
		error = result
	}
}
