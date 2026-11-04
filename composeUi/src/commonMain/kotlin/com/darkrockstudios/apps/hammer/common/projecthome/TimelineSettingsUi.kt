package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarDef
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.StoryCalendar
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings
import com.darkrockstudios.apps.hammer.common.components.projecthome.timelinesettings.TimelineSettings.ModeSwitchPrompt
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.lastOf
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdDragHandle
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDropdown
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSection
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdWarningNotice
import com.darkrockstudios.apps.hammer.common.compose.designsystem.storyUnitName
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.reorderable.DragDropList
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimelineSummary
import com.darkrockstudios.apps.hammer.common.timeline.LaneSwatch
import com.darkrockstudios.apps.hammer.common.timeline.TimelineSwitcherDialogs
import com.darkrockstudios.apps.hammer.common.timeline.toastText
import com.darkrockstudios.apps.hammer.timeline_mode_dated
import com.darkrockstudios.apps.hammer.timeline_mode_dialog_cancel
import com.darkrockstudios.apps.hammer.timeline_mode_dialog_close
import com.darkrockstudios.apps.hammer.timeline_mode_dialog_confirm
import com.darkrockstudios.apps.hammer.timeline_mode_dialog_dated_message
import com.darkrockstudios.apps.hammer.timeline_mode_dialog_dated_title
import com.darkrockstudios.apps.hammer.timeline_mode_dialog_dated_title_named
import com.darkrockstudios.apps.hammer.timeline_mode_dialog_entry_count
import com.darkrockstudios.apps.hammer.timeline_mode_dialog_manual_message
import com.darkrockstudios.apps.hammer.timeline_mode_dialog_manual_title
import com.darkrockstudios.apps.hammer.timeline_mode_dialog_manual_title_named
import com.darkrockstudios.apps.hammer.timeline_mode_dialog_space_evenly
import com.darkrockstudios.apps.hammer.timeline_mode_manual
import com.darkrockstudios.apps.hammer.timeline_preset_gregorian
import com.darkrockstudios.apps.hammer.timeline_preset_story
import com.darkrockstudios.apps.hammer.timeline_preset_warning_cancel
import com.darkrockstudios.apps.hammer.timeline_preset_warning_confirm
import com.darkrockstudios.apps.hammer.timeline_preset_warning_label
import com.darkrockstudios.apps.hammer.timeline_preset_warning_label_named
import com.darkrockstudios.apps.hammer.timeline_preset_warning_message
import com.darkrockstudios.apps.hammer.timeline_settings_caption
import com.darkrockstudios.apps.hammer.timeline_settings_coarsest_label
import com.darkrockstudios.apps.hammer.timeline_settings_delete
import com.darkrockstudios.apps.hammer.timeline_settings_editing_label
import com.darkrockstudios.apps.hammer.timeline_settings_epoch_hint
import com.darkrockstudios.apps.hammer.timeline_settings_epoch_label
import com.darkrockstudios.apps.hammer.timeline_settings_epoch_placeholder
import com.darkrockstudios.apps.hammer.timeline_settings_lanes_caption
import com.darkrockstudios.apps.hammer.timeline_settings_lanes_empty
import com.darkrockstudios.apps.hammer.timeline_settings_lanes_label
import com.darkrockstudios.apps.hammer.timeline_settings_mode_label
import com.darkrockstudios.apps.hammer.timeline_settings_new
import com.darkrockstudios.apps.hammer.timeline_settings_preset_label
import com.darkrockstudios.apps.hammer.timeline_settings_rename
import com.darkrockstudios.apps.hammer.timeline_settings_section_title
import com.darkrockstudios.apps.hammer.timeline_settings_timeline_meta
import com.darkrockstudios.apps.hammer.timeline_settings_timelines_label
import org.jetbrains.compose.resources.pluralStringResource

const val TIMELINE_SETTINGS_MODE_TAG = "timeline-settings-mode"
const val TIMELINE_SETTINGS_PRESET_TAG = "timeline-settings-preset"
const val TIMELINE_SETTINGS_EPOCH_TAG = "timeline-settings-epoch"
const val TIMELINE_SETTINGS_LANES_TAG = "timeline-settings-lanes"
const val TIMELINE_SETTINGS_PRESET_WARNING_TAG = "timeline-settings-preset-warning"
const val TIMELINE_MODE_DIALOG_CONFIRM_TAG = "timeline-mode-dialog-confirm"
const val TIMELINE_MODE_DIALOG_UNIT_TAG = "timeline-mode-dialog-unit"
const val TIMELINE_MODE_DIALOG_COUNT_TAG = "timeline-mode-dialog-count"
const val TIMELINE_SETTINGS_EDITING_TAG = "timeline-settings-editing"
const val TIMELINE_SETTINGS_NEW_TAG = "timeline-settings-new"

fun timelineSettingsRowTag(timelineId: String) = "timeline-settings-row-$timelineId"
fun timelineSettingsRenameTag(timelineId: String) = "timeline-settings-rename-$timelineId"
fun timelineSettingsDeleteTag(timelineId: String) = "timeline-settings-delete-$timelineId"

private val Modes = listOf(TimelineMode.MANUAL, TimelineMode.DATED)
private val Presets = listOf(CalendarPreset.STORY, CalendarPreset.GREGORIAN)
private val LaneRowHeight = 44.dp
private const val MAX_VISIBLE_LANES = 8

/**
 * The project's timelines, then the mode, calendar and lane order of the one being edited, as one
 * section of the project settings.
 */
@Composable
fun TimelineSettingsUi(
	component: TimelineSettings,
	section: Int,
	modifier: Modifier = Modifier,
) {
	val state by component.state.subscribeAsState()
	val def = state.def
	val dated = def.mode == TimelineMode.DATED
	val calendar = def.calendar ?: CalendarDef()
	// Timelines go by name once there is more than one; until then the prompts say "the timeline".
	val named = state.timelines.size >= 2

	HdHairlineSection(
		modifier = modifier,
		section = section,
		title = Res.string.timeline_settings_section_title.get(),
		contentSpacing = 18.dp,
	) {
		if (state.timelines.isNotEmpty()) {
			TimelineList(
				timelines = state.timelines,
				onNew = component::requestCreate,
				onRename = { summary -> component.requestRename(summary.def.id) },
				onDelete = { summary -> component.requestDelete(summary.def.id) },
			)
		}

		if (named) {
			val editing = state.timelines.firstOrNull { it.def.id == state.editingId } ?: state.timelines.first()
			HdHairlineDropdown(
				options = state.timelines,
				selected = editing,
				onSelect = { component.selectTimeline(it.def.id) },
				label = { it.def.name },
				title = Res.string.timeline_settings_editing_label.get(),
				modifier = Modifier.testTag(TIMELINE_SETTINGS_EDITING_TAG),
			)
		}

		Text(
			text = Res.string.timeline_settings_caption.get(),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)

		HdHairlineSegmentedPicker(
			options = Modes,
			selected = def.mode,
			onSelect = component::requestMode,
			label = { it.label() },
			title = Res.string.timeline_settings_mode_label.get(),
			modifier = Modifier.testTag(TIMELINE_SETTINGS_MODE_TAG),
		)

		HdHairlineSegmentedPicker(
			options = Presets,
			selected = calendar.preset,
			onSelect = component::setPreset,
			label = { it.label() },
			title = Res.string.timeline_settings_preset_label.get(),
			enabled = dated,
			modifier = Modifier.testTag(TIMELINE_SETTINGS_PRESET_TAG),
		)

		val pending = state.pendingCalendar
		val lost = state.datesLostOnPresetSwitch
		if (pending != null && lost != null) {
			PresetWarning(
				lost = lost,
				timelineName = state.presetSwitchTimeline?.name?.takeIf { named },
				onConfirm = component::confirmPresetSwitch,
				onCancel = component::cancelPresetSwitch,
			)
		}

		if (dated && calendar.preset == CalendarPreset.STORY) {
			EpochField(initial = calendar.epochName.orEmpty(), onChange = component::setEpochName)

			val units = StoryCalendar.units(CalendarPreset.STORY).map { it.name }
			HdHairlineDropdown(
				options = units,
				selected = calendar.coarsestUnit ?: units.first(),
				onSelect = component::setCoarsestUnit,
				label = { storyUnitName(it) },
				title = Res.string.timeline_settings_coarsest_label.get(),
			)
		}

		LaneOrder(lanes = state.lanes, onReorder = component::setLaneOrder)

		state.error?.let { error ->
			val strRes = rememberStrRes()
			var message by remember { mutableStateOf<String?>(null) }
			LaunchedEffect(error) { message = error.toastText(strRes) }
			message?.let { HdMonoLabel(text = it, color = MaterialTheme.colorScheme.error) }
		}
	}

	ModeSwitchDialog(
		prompt = state.pendingModeSwitch,
		named = named,
		onUnit = component::setSpacingUnit,
		onConfirm = component::confirmModeSwitch,
		onCancel = component::cancelModeSwitch,
	)

	TimelineSwitcherDialogs(
		switcher = state.switcher,
		onCreate = component::createTimeline,
		onRename = component::renameTimeline,
		onConfirmDelete = component::confirmDelete,
		onDismiss = component::dismissDialog,
	)
}

/** Every timeline with its mode and event count, rename and delete (never main's), then `+ New timeline`. */
@Composable
private fun TimelineList(
	timelines: List<TimelineSummary>,
	onNew: () -> Unit,
	onRename: (TimelineSummary) -> Unit,
	onDelete: (TimelineSummary) -> Unit,
) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		HdMonoLabel(text = Res.string.timeline_settings_timelines_label.get())
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
		) {
			timelines.forEachIndexed { index, summary ->
				if (index > 0) HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
				TimelineRow(summary = summary, onRename = { onRename(summary) }, onDelete = { onDelete(summary) })
			}
		}
		HdHairlineButton(
			label = Res.string.timeline_settings_new.get(),
			onClick = onNew,
			modifier = Modifier.testTag(TIMELINE_SETTINGS_NEW_TAG),
		)
	}
}

@Composable
private fun TimelineRow(summary: TimelineSummary, onRename: () -> Unit, onDelete: () -> Unit) {
	val def = summary.def
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.L, vertical = Ui.Padding.M)
			.testTag(timelineSettingsRowTag(def.id)),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(
				text = def.name,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			HdMonoLabel(
				text = pluralStringResource(
					Res.plurals.timeline_settings_timeline_meta,
					summary.eventCount,
					def.mode.label(),
					summary.eventCount,
				),
			)
		}
		HdHairlineButton(
			label = Res.string.timeline_settings_rename.get(),
			onClick = onRename,
			modifier = Modifier.testTag(timelineSettingsRenameTag(def.id)),
		)
		if (def.id != TimelineDef.MAIN_ID) {
			HdHairlineButton(
				label = Res.string.timeline_settings_delete.get(),
				onClick = onDelete,
				danger = true,
				modifier = Modifier.testTag(timelineSettingsDeleteTag(def.id)),
			)
		}
	}
}

@Composable
private fun TimelineMode.label(): String = when (this) {
	TimelineMode.MANUAL -> Res.string.timeline_mode_manual
	TimelineMode.DATED -> Res.string.timeline_mode_dated
}.get()

@Composable
private fun CalendarPreset.label(): String = when (this) {
	CalendarPreset.STORY -> Res.string.timeline_preset_story
	CalendarPreset.GREGORIAN -> Res.string.timeline_preset_gregorian
}.get()

/** Commits trimmed text; keeps what is typed until the stored name changes to something else. */
@Composable
private fun EpochField(initial: String, onChange: (String) -> Unit) {
	var text by remember { mutableStateOf(initial) }
	LaunchedEffect(initial) {
		if (initial.trim() != text.trim()) text = initial
	}
	HdHairlineField(
		label = Res.string.timeline_settings_epoch_label.get(),
		value = text,
		onValueChange = {
			text = it
			onChange(it)
		},
		hint = Res.string.timeline_settings_epoch_hint.get(),
		placeholder = Res.string.timeline_settings_epoch_placeholder.get(),
		testTag = TIMELINE_SETTINGS_EPOCH_TAG,
	)
}

/** [timelineName] names the timeline the dates are on; null leaves it as "the timeline". */
@Composable
private fun PresetWarning(lost: Int, timelineName: String?, onConfirm: () -> Unit, onCancel: () -> Unit) {
	Column(
		modifier = Modifier.testTag(TIMELINE_SETTINGS_PRESET_WARNING_TAG),
		verticalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdWarningNotice(
			label = timelineName?.let { Res.string.timeline_preset_warning_label_named.get(it) }
				?: Res.string.timeline_preset_warning_label.get(),
			message = pluralStringResource(Res.plurals.timeline_preset_warning_message, lost, lost),
		)
		Row(horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
			Spacer(modifier = Modifier.weight(1f))
			HdHairlineButton(label = Res.string.timeline_preset_warning_cancel.get(), onClick = onCancel)
			HdHairlineButton(
				label = Res.string.timeline_preset_warning_confirm.get(),
				onClick = onConfirm,
				emphasised = true,
			)
		}
	}
}

/** Every lane as a draggable row: swatch in its colour, name, grip. */
@Composable
private fun LaneOrder(lanes: List<String>, onReorder: (List<String>) -> Unit) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		HdMonoLabel(text = Res.string.timeline_settings_lanes_label.get())
		if (lanes.isEmpty()) {
			Text(
				text = Res.string.timeline_settings_lanes_empty.get(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			return@Column
		}
		Text(
			text = Res.string.timeline_settings_lanes_caption.get(),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		// The settings page scrolls, so the list gets a bounded height of its own.
		val visible = lanes.size.coerceAtMost(MAX_VISIBLE_LANES)
		DragDropList(
			items = lanes,
			key = { _, lane -> lane },
			onMove = { from, to ->
				onReorder(lanes.toMutableList().apply { add(to, removeAt(from)) })
			},
			modifier = Modifier
				.fillMaxWidth()
				.height(LaneRowHeight * visible)
				.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape)
				.testTag(TIMELINE_SETTINGS_LANES_TAG),
		) { lane, dragging ->
			LaneRow(lane = lane, index = lanes.indexOf(lane), dragging = dragging)
		}
	}
}

@Composable
private fun LaneRow(lane: String, index: Int, dragging: Boolean) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(LaneRowHeight)
			.background(
				if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
			)
			.padding(horizontal = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
	) {
		LaneSwatch(lane = lane, index = index, size = 12.dp)
		Text(
			text = lane,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f),
		)
		HdDragHandle()
	}
}

/** With [named] set, the title names the prompt's timeline; the count is always only its entries. */
@Composable
private fun ModeSwitchDialog(
	prompt: ModeSwitchPrompt?,
	named: Boolean,
	onUnit: (String) -> Unit,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
) {
	val shown = lastOf(prompt)
	AnimatedDialog(visible = prompt != null, onCloseRequest = onCancel) {
		val current = shown ?: return@AnimatedDialog
		val name = current.timeline.name
		val title = when (current) {
			is ModeSwitchPrompt.ToDated -> if (named) {
				Res.string.timeline_mode_dialog_dated_title_named.get(name)
			} else {
				Res.string.timeline_mode_dialog_dated_title.get()
			}

			is ModeSwitchPrompt.ToManual -> if (named) {
				Res.string.timeline_mode_dialog_manual_title_named.get(name)
			} else {
				Res.string.timeline_mode_dialog_manual_title.get()
			}
		}
		HdHairlineDialogShell(
			title = title,
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.timeline_mode_dialog_close.get(),
		) {
			Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
				HdMonoLabel(
					text = pluralStringResource(
						Res.plurals.timeline_mode_dialog_entry_count,
						current.eventCount,
						current.eventCount,
					),
					modifier = Modifier.testTag(TIMELINE_MODE_DIALOG_COUNT_TAG),
				)
				when (current) {
					is ModeSwitchPrompt.ToDated -> {
						Text(
							text = Res.string.timeline_mode_dialog_dated_message.get(),
							style = MaterialTheme.typography.bodyMedium,
						)
						HdHairlineDropdown(
							options = current.units.map { it.name },
							selected = current.selectedUnit,
							onSelect = onUnit,
							label = { storyUnitName(it) },
							title = Res.string.timeline_mode_dialog_space_evenly.get(),
							modifier = Modifier.testTag(TIMELINE_MODE_DIALOG_UNIT_TAG),
						)
					}

					is ModeSwitchPrompt.ToManual -> Text(
						text = Res.string.timeline_mode_dialog_manual_message.get(),
						style = MaterialTheme.typography.bodyMedium,
					)
				}
				Row(horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
					Spacer(modifier = Modifier.weight(1f))
					HdHairlineButton(
						label = Res.string.timeline_mode_dialog_cancel.get(),
						onClick = { requestDismiss() },
					)
					HdHairlineButton(
						label = Res.string.timeline_mode_dialog_confirm.get(),
						onClick = onConfirm,
						emphasised = true,
						modifier = Modifier.testTag(TIMELINE_MODE_DIALOG_CONFIRM_TAG),
					)
				}
			}
		}
	}
}
