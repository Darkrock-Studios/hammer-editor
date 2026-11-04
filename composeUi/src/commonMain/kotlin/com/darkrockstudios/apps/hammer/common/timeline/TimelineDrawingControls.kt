package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdClearGlyph
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSortPill
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.truncateWithEllipsis
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineFrame
import com.darkrockstudios.apps.hammer.common.timeline.layout.TimelineView
import com.darkrockstudios.apps.hammer.timeline_drawing_menu
import com.darkrockstudios.apps.hammer.timeline_drawing_menu_frame
import com.darkrockstudios.apps.hammer.timeline_drawing_menu_view
import com.darkrockstudios.apps.hammer.timeline_frame_back_to_everything
import com.darkrockstudios.apps.hammer.timeline_frame_band_description
import com.darkrockstudios.apps.hammer.timeline_frame_everything
import com.darkrockstudios.apps.hammer.timeline_frame_story
import com.darkrockstudios.apps.hammer.timeline_view_sequence
import com.darkrockstudios.apps.hammer.timeline_view_to_scale

/**
 * An invisible stand-in for an event's mark that the dialog opened from it grows out of. Hidden while
 * that dialog is open; its reappearance is the transition the dialog shrinks back into.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun EventMarkSource(
	eventId: Int,
	visible: Boolean,
	sharedTransitionScope: SharedTransitionScope,
	modifier: Modifier = Modifier,
) {
	AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None, modifier = modifier) {
		with(sharedTransitionScope) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.sharedElement(
						sharedContentState = rememberSharedContentState(eventDialogKey(eventId)),
						animatedVisibilityScope = this@AnimatedVisibility,
					),
			)
		}
	}
}

/**
 * The frame the drawing is on, over its top corner where it never scrolls away; tapping it returns
 * to Everything.
 */
@Composable
internal fun FramePill(label: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
	val description = Res.string.timeline_frame_back_to_everything.get()
	Row(
		modifier = modifier
			.testTag(TIME_LINE_FRAME_PILL_TAG)
			.border(Dp.Hairline, MaterialTheme.colorScheme.outline, RectangleShape)
			.background(MaterialTheme.colorScheme.surface)
			.clickable(onClick = onClear)
			.semantics(mergeDescendants = true) {
				contentDescription = description
				role = Role.Button
			}
			.padding(start = Ui.Padding.M, end = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(2.dp),
	) {
		HdMonoLabel(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
		HdClearGlyph(onClick = null)
	}
}

/**
 * `VIEW  SEQUENCE · EVERYTHING` at the end of the filter row, opening one menu with the drawing
 * (Sequence or To scale) above the frame (Everything or Story). A framed band shows its title as the
 * frame; the pill on the drawing returns it to Everything. [compact] opens the same menu from a ⋮
 * button, for a phone's header.
 */
@Composable
internal fun DrawingMenu(
	state: TimeLineOverview.State,
	bandTitle: String?,
	onView: (TimelineView) -> Unit,
	onFrame: (TimelineFrame) -> Unit,
	compact: Boolean = false,
) {
	var expanded by remember { mutableStateOf(false) }
	val sequence = Res.string.timeline_view_sequence.get()
	val toScale = Res.string.timeline_view_to_scale.get()
	val everything = Res.string.timeline_frame_everything.get()
	val story = Res.string.timeline_frame_story.get()
	val bandDescription = bandTitle?.let { Res.string.timeline_frame_band_description.get(it) }
	val viewLabel: (TimelineView) -> String = { view ->
		when (view) {
			TimelineView.SEQUENCE -> sequence
			TimelineView.TO_SCALE -> toScale
		}
	}
	val frameLabel: (TimelineFrame) -> String = { frame ->
		when (frame) {
			TimelineFrame.Everything -> everything
			TimelineFrame.Story -> story
			is TimelineFrame.Band -> bandTitle.orEmpty().truncateWithEllipsis(BAND_CELL_CHARS)
		}
	}
	val menuLabel = Res.string.timeline_drawing_menu.get()
	val current = "${viewLabel(state.view)} · ${frameLabel(state.frame)}"
	Box {
		if (compact) {
			IconButton(
				onClick = { expanded = true },
				modifier = Modifier
					.testTag(TIME_LINE_DRAWING_MENU_TAG)
					.semantics { contentDescription = bandDescription ?: "$menuLabel: $current" },
			) {
				Icon(
					imageVector = Icons.Default.MoreVert,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurface,
				)
			}
		} else {
			HdSortPill(
				label = menuLabel,
				value = current,
				onClick = { expanded = true },
				modifier = Modifier
					.testTag(TIME_LINE_DRAWING_MENU_TAG)
					.then(if (bandDescription != null) Modifier.semantics { contentDescription = bandDescription } else Modifier),
			)
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			MenuSection(Res.string.timeline_drawing_menu_view.get())
			TimelineView.entries.forEach { view ->
				MenuChoice(label = viewLabel(view), selected = view == state.view) {
					onView(view)
					expanded = false
				}
			}
			HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
			MenuSection(Res.string.timeline_drawing_menu_frame.get())
			listOf(TimelineFrame.Everything, TimelineFrame.Story).forEach { frame ->
				MenuChoice(
					label = frameLabel(frame),
					selected = frame == state.frame,
					enabled = frame != TimelineFrame.Story || state.storyFrameAvailable,
				) {
					onFrame(frame)
					expanded = false
				}
			}
		}
	}
}

@Composable
internal fun MenuSection(title: String) {
	HdMonoLabel(text = title, modifier = Modifier.padding(horizontal = Ui.Padding.L, vertical = Ui.Padding.S))
}

@Composable
private fun MenuChoice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
	DropdownMenuItem(
		text = {
			Text(
				text = label,
				style = MaterialTheme.typography.bodyMedium,
				color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
				fontWeight = if (selected) FontWeight.Bold else null,
			)
		},
		onClick = onClick,
		enabled = enabled,
		modifier = Modifier.testTag(menuChoiceTag(label)),
	)
}

internal fun menuChoiceTag(label: String) = "Timeline Drawing Menu: $label"

internal const val BAND_CELL_CHARS = 18
