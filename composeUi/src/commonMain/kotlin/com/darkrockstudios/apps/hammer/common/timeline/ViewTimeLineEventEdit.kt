package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.common.components.timeline.ViewTimeLineEvent
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.markdowneditor.MarkdownEditField
import com.darkrockstudios.apps.hammer.common.compose.resources.get

/** The fields every kind has: when (dates on a DATED timeline), lane and milestone. */
@Composable
internal fun PlacementFields(
	state: ViewTimeLineEvent.State,
	component: ViewTimeLineEvent,
	dateText: String,
	modifier: Modifier = Modifier,
	onFocusChanged: (Boolean) -> Unit = {},
) {
	var dateFocused by remember { mutableStateOf(false) }
	var laneFocused by remember { mutableStateOf(false) }
	Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
		if (state.isDated) {
			DateRows(
				calendar = state.calendar ?: CalendarDef(),
				start = state.start,
				end = state.end,
				onStartChange = component::onStartChanged,
				onEndChange = component::onEndChanged,
				onFocusChanged = {
					dateFocused = it
					onFocusChanged(dateFocused || laneFocused)
				},
				startTag = TIME_LINE_VIEW_START_TAG,
				endTag = TIME_LINE_VIEW_END_TAG,
			)
		} else {
			HdHairlineField(
				label = Res.string.timeline_when_label.get(),
				value = dateText,
				onValueChange = component::onDateTextChanged,
				hint = Res.string.timeline_view_date_hint.get(),
				placeholder = Res.string.timeline_view_date_placeholder.get(),
				onFocusChanged = {
					dateFocused = it
					onFocusChanged(dateFocused || laneFocused)
				},
				testTag = TIME_LINE_VIEW_WHEN_TAG,
			)
		}
		LaneField(
			value = state.lane,
			onValueChange = component::onLaneChanged,
			lanes = state.lanes,
			suggest = component::suggestLanes,
			onFocusChanged = {
				laneFocused = it
				onFocusChanged(dateFocused || laneFocused)
			},
			testTag = TIME_LINE_VIEW_LANE_TAG,
		)
		HdHairlineToggleRow(
			checked = state.milestone,
			onCheckedChange = component::onMilestoneChanged,
			label = Res.string.timeline_milestone_label.get(),
			hint = Res.string.timeline_milestone_hint.get(),
		)
	}
}

@Composable
internal fun EditBody(
	state: ViewTimeLineEvent.State,
	component: ViewTimeLineEvent,
	dateText: String,
	eventText: String,
	onAddReferences: () -> Unit,
	modifier: Modifier = Modifier,
) {
	// Each field hides while the body editor has focus, like the tags; kept visible while it holds
	// focus itself so it stays reachable. CollapseWhileTyping centralizes the IME + debounce rules.
	var titleFocused by remember { mutableStateOf(false) }
	var placementFocused by remember { mutableStateOf(false) }
	val fieldPadding = Modifier.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L)

	Column(modifier = modifier.fillMaxWidth()) {
		CollapseWhileTyping(keepVisible = titleFocused) {
			HdHairlineField(
				label = Res.string.timeline_title_label.get(),
				value = state.title,
				onValueChange = component::onTitleChanged,
				hint = Res.string.timeline_title_hint.get(),
				placeholder = Res.string.timeline_title_placeholder.get(),
				onFocusChanged = { titleFocused = it },
				modifier = fieldPadding,
			)
		}

		CollapseWhileTyping(keepVisible = placementFocused) {
			PlacementFields(
				state = state,
				component = component,
				dateText = dateText,
				onFocusChanged = { placementFocused = it },
				modifier = fieldPadding,
			)
		}

		HdHairlineTagField(
			label = Res.string.timeline_create_tags_label.get(),
			tags = state.tags.toList(),
			onTagsChange = { component.onTagsChanged(it.toSet()) },
			hint = Res.string.timeline_create_tags_hint.get(),
			placeholder = Res.string.timeline_create_tags_placeholder.get(),
			suggestTags = component::suggestTags,
			modifier = fieldPadding,
		)

		CollapseWhileTyping {
			ReferencesSection(
				references = state.references,
				onAdd = onAddReferences,
				onRemove = { component.removeReference(it.id) },
				onOpen = component::openReference,
				modifier = fieldPadding,
			)
		}

		CollapseWhileTyping {
			HorizontalDivider(
				thickness = 2.dp,
				color = MaterialTheme.colorScheme.outline,
			)
		}

		MarkdownEditField(
			initialMarkdown = eventText,
			onMarkdownChanged = component::onEventTextChanged,
			enableSpellChecking = state.spellCheckAllowed,
			imagePicker = component::pickImageForText,
			contentPadding = PaddingValues(Ui.Padding.XL),
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f),
		)
	}
}
