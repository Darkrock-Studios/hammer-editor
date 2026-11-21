package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.markdown.countWords
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPulsingDot
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdStatusFooter
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEvent

@Composable
internal fun CrumbRow(
	onClose: () -> Unit,
	onFrame: (() -> Unit)?,
	menuSlot: @Composable () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		HdCrumbBackLink(
			label = Res.string.timeline_view_crumb_root.get(),
			onClick = onClose,
			onClickLabel = Res.string.timeline_view_close_button.get(),
		)
		Spacer(modifier = Modifier.weight(1f))
		if (onFrame != null) {
			HdHairlineButton(
				label = Res.string.timeline_event_frame.get(),
				onClick = onFrame,
				modifier = Modifier.testTag(TIME_LINE_VIEW_FRAME_TAG),
			)
		}
		menuSlot()
	}
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun StampRow(
	isEditing: Boolean,
	state: ViewTimeLineEvent.State,
	savedDate: String?,
	sharedTransitionScope: SharedTransitionScope,
	animatedVisibilityScope: AnimatedVisibilityScope,
	onEdit: () -> Unit,
	onSave: () -> Unit,
	onCancel: () -> Unit,
	onMove: (() -> Unit)? = null,
) {
	val event = state.event
	val moveShown = !isEditing && onMove != null
	HdDetailStampRow(
		stackActionsWhenNarrow = isEditing || moveShown,
		leading = {
			if (isEditing) {
				HdMonoLabel(
					text = "§ IV · ${Res.string.timeline_view_label_editing.get()}",
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				HdPulsingDot()
			} else {
				HdMonoLabel(
					text = state.kind.label(),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				MetaRule()
				with(sharedTransitionScope) {
					HdMonoLabel(
						text = savedDate?.takeIf { it.isNotBlank() } ?: Res.string.timeline_view_undated.get(),
						modifier = Modifier.sharedElement(
							sharedContentState = rememberSharedContentState(
								key = "timeline-date-${event?.id}",
							),
							animatedVisibilityScope = animatedVisibilityScope,
						),
					)
				}
				val lane = event?.lane
				if (lane != null) {
					MetaRule()
					val index = state.laneIndex
					if (index != null) LaneSwatch(lane = lane, index = index)
					HdMonoLabel(text = lane, maxLines = 1)
				}
			}
		},
		meta = if (isEditing) {
			{
				MetaRule()
				HdMonoLabel(text = Res.string.timeline_view_status_unsaved.get())
			}
		} else {
			null
		},
		actions = {
			if (isEditing) {
				HdHairlineButton(
					label = Res.string.timeline_view_save_button.get(),
					onClick = onSave,
					emphasised = true,
				)
				HdHairlineButton(
					label = Res.string.timeline_view_cancel_button.get(),
					onClick = onCancel,
				)
			} else {
				if (onMove != null) {
					HdHairlineButton(
						label = Res.string.timeline_view_menu_move.get(),
						onClick = onMove,
						modifier = Modifier.testTag(TIME_LINE_VIEW_MOVE_TAG),
					)
				}
				HdHairlineButton(
					label = Res.string.timeline_view_edit_button.get(),
					onClick = onEdit,
				)
			}
		},
	)
}

@Composable
private fun MetaRule() {
	Box(
		modifier = Modifier
			.height(14.dp)
			.width(Dp.Hairline)
			.background(MaterialTheme.colorScheme.outlineVariant),
	)
}

@Composable
internal fun ViewFolioFooter(event: TimeLineEvent?, eventText: String?) {
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.M),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		event?.id?.let { id ->
			HdEntityId(prefix = "TIME", id = id)
		}
		Spacer(modifier = Modifier.weight(1f))
		if (eventText != null) {
			HdMonoLabel(
				text = Res.string.timeline_view_word_count_short.get(countWords(eventText)),
			)
		}
	}
}

@Composable
internal fun EditStatusFooter(text: String) {
	val words = remember(text) { countWords(text) }
	HdStatusFooter {
		HdMonoLabel(text = Res.string.timeline_view_status_word_char.get(words, text.length))
		Spacer(modifier = Modifier.weight(1f))
		HdMonoLabel(text = Res.string.timeline_view_status_hint.get())
	}
}
