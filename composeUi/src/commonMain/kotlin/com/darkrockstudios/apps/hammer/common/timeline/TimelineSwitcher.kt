package com.darkrockstudios.apps.hammer.common.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.base.timeline.CalendarPreset
import com.darkrockstudios.apps.hammer.base.timeline.TimelineDef
import com.darkrockstudios.apps.hammer.base.timeline.TimelineMode
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLine
import com.darkrockstudios.apps.hammer.common.components.timeline.TimeLineOverview
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSelection
import com.darkrockstudios.apps.hammer.common.components.timeline.TimelineSwitcher
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDialogShell
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineField
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineSegmentedPicker
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdNoticeStrip
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdSectionHeader
import com.darkrockstudios.apps.hammer.common.compose.lastOf
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.timelinerepository.TimeLineEventError
import com.darkrockstudios.apps.hammer.timeline_delete_confirm
import com.darkrockstudios.apps.hammer.timeline_delete_detail
import com.darkrockstudios.apps.hammer.timeline_delete_title
import com.darkrockstudios.apps.hammer.timeline_deleted_bucket
import com.darkrockstudios.apps.hammer.timeline_deleted_delete_all
import com.darkrockstudios.apps.hammer.timeline_deleted_delete_detail
import com.darkrockstudios.apps.hammer.timeline_deleted_delete_title
import com.darkrockstudios.apps.hammer.timeline_deleted_move_all
import com.darkrockstudios.apps.hammer.timeline_deleted_notice_detail
import com.darkrockstudios.apps.hammer.timeline_deleted_notice_title
import com.darkrockstudios.apps.hammer.timeline_header
import com.darkrockstudios.apps.hammer.timeline_mode_dated
import com.darkrockstudios.apps.hammer.timeline_mode_manual
import com.darkrockstudios.apps.hammer.timeline_name_label
import com.darkrockstudios.apps.hammer.timeline_new_confirm
import com.darkrockstudios.apps.hammer.timeline_new_title
import com.darkrockstudios.apps.hammer.timeline_preset_gregorian
import com.darkrockstudios.apps.hammer.timeline_preset_story
import com.darkrockstudios.apps.hammer.timeline_rename_confirm
import com.darkrockstudios.apps.hammer.timeline_rename_title
import com.darkrockstudios.apps.hammer.timeline_settings_mode_label
import com.darkrockstudios.apps.hammer.timeline_settings_preset_label
import com.darkrockstudios.apps.hammer.timeline_switcher_cancel
import com.darkrockstudios.apps.hammer.timeline_switcher_close
import com.darkrockstudios.apps.hammer.timeline_switcher_delete
import com.darkrockstudios.apps.hammer.timeline_switcher_new
import com.darkrockstudios.apps.hammer.timeline_switcher_rename
import com.darkrockstudios.apps.hammer.timeline_switcher_section
import com.darkrockstudios.apps.hammer.timeline_switcher_settings
import org.jetbrains.compose.resources.pluralStringResource

const val TIME_LINE_SWITCHER_TAG = "Timeline Switcher"
const val TIME_LINE_SWITCHER_DELETED_TAG = "Timeline Switcher Deleted"
const val TIME_LINE_SWITCHER_NEW_TAG = "Timeline Switcher New"
const val TIME_LINE_SWITCHER_RENAME_TAG = "Timeline Switcher Rename"
const val TIME_LINE_SWITCHER_DELETE_TAG = "Timeline Switcher Delete"
const val TIME_LINE_SWITCHER_SETTINGS_TAG = "Timeline Switcher Settings"
const val TIME_LINE_NEW_NAME_TAG = "Timeline New Name"
const val TIME_LINE_NEW_MODE_TAG = "Timeline New Mode"
const val TIME_LINE_NEW_PRESET_TAG = "Timeline New Preset"
const val TIME_LINE_NEW_CONFIRM_TAG = "Timeline New Confirm"
const val TIME_LINE_RENAME_NAME_TAG = "Timeline Rename Name"
const val TIME_LINE_RENAME_CONFIRM_TAG = "Timeline Rename Confirm"
const val TIME_LINE_DELETE_CONFIRM_TAG = "Timeline Delete Confirm"
const val TIME_LINE_SWITCHER_ERROR_TAG = "Timeline Switcher Error"
const val TIME_LINE_DELETED_NOTICE_TAG = "Timeline Deleted Notice"
const val TIME_LINE_DELETED_DELETE_CONFIRM_TAG = "Timeline Deleted Delete Confirm"

fun timelineMoveAllTargetTag(timelineId: String) = "Timeline Move All To: $timelineId"

fun timelineSwitcherRowTag(timelineId: String) = "Timeline Switcher Row: $timelineId"

private val Modes = listOf(TimelineMode.MANUAL, TimelineMode.DATED)
private val Presets = listOf(CalendarPreset.STORY, CalendarPreset.GREGORIAN)

/** What the header switcher's menu asks of the timeline screen. */
@Immutable
class TimelineSwitcherActions(
	val onSelect: (TimelineSelection) -> Unit,
	val onNew: () -> Unit,
	val onRename: (timelineId: String) -> Unit,
	val onDelete: (timelineId: String) -> Unit,
	val onSettings: () -> Unit,
) {
	companion object {
		fun of(timeline: TimeLine) = TimelineSwitcherActions(
			onSelect = timeline::selectTimeline,
			onNew = timeline::requestCreateTimeline,
			onRename = timeline::requestRenameTimeline,
			onDelete = timeline::requestDeleteTimeline,
			onSettings = timeline::showTimelineSettings,
		)

		val None = TimelineSwitcherActions({}, {}, {}, {}, {})
	}
}

/**
 * What the masthead calls the timeline shown: `Timeline` while main is the only one, else its name;
 * the deleted-timeline bucket has its own.
 */
@Composable
internal fun TimeLineOverview.State.switcherTitle(): String = when {
	isOrphans -> Res.string.timeline_deleted_bucket.get()
	timelines.size >= 2 -> selectedTimeline?.def?.name ?: Res.string.timeline_header.get()
	else -> Res.string.timeline_header.get()
}

/**
 * The masthead's `§ IV  Title ▾` header, whose title opens the timeline switcher. The chevron shows
 * even when main is the only timeline, as the menu is where a new one is made.
 */
@Composable
internal fun TimelineSwitcherHeader(
	state: TimeLineOverview.State,
	actions: TimelineSwitcherActions,
	modifier: Modifier = Modifier,
	trailing: @Composable RowScope.() -> Unit = {},
) {
	var expanded by remember { mutableStateOf(false) }
	Box(modifier = modifier) {
		HdSectionHeader(
			section = 4,
			title = state.switcherTitle(),
			onTitleClick = { expanded = true },
			titleMenu = true,
			modifier = Modifier.testTag(TIME_LINE_SWITCHER_TAG),
			trailing = trailing,
		)
		DropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
			modifier = Modifier.widthIn(min = 240.dp, max = 360.dp),
		) {
			TimelineSwitcherMenuItems(state = state, actions = actions, close = { expanded = false })
		}
	}
}

/**
 * The switcher menu: every timeline with its event count, the shown one bold; the deleted-timeline
 * bucket in the warning colour when it holds events; then new, rename, delete and settings. Rename and
 * delete act on the timeline shown, and main cannot be deleted.
 */
@Composable
internal fun ColumnScope.TimelineSwitcherMenuItems(
	state: TimeLineOverview.State,
	actions: TimelineSwitcherActions,
	close: () -> Unit,
) {
	MenuSection(Res.string.timeline_switcher_section.get())
	state.timelines.forEach { summary ->
		val selected = state.selection == TimelineSelection.Def(summary.def.id)
		SwitcherRow(
			name = summary.def.name,
			count = summary.eventCount,
			selected = selected,
			tag = timelineSwitcherRowTag(summary.def.id),
		) {
			close()
			actions.onSelect(TimelineSelection.Def(summary.def.id))
		}
	}
	if (state.orphanCount > 0) {
		SwitcherRow(
			name = Res.string.timeline_deleted_bucket.get(),
			count = state.orphanCount,
			selected = state.isOrphans,
			tag = TIME_LINE_SWITCHER_DELETED_TAG,
			color = LocalHammerColors.current.warning,
		) {
			close()
			actions.onSelect(TimelineSelection.Orphans)
		}
	}
	HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
	SwitcherAction(Res.string.timeline_switcher_new.get(), TIME_LINE_SWITCHER_NEW_TAG) {
		close()
		actions.onNew()
	}
	state.selectedTimeline?.def?.let { def ->
		SwitcherAction(Res.string.timeline_switcher_rename.get(def.name), TIME_LINE_SWITCHER_RENAME_TAG) {
			close()
			actions.onRename(def.id)
		}
		if (def.id != TimelineDef.MAIN_ID) {
			SwitcherAction(Res.string.timeline_switcher_delete.get(def.name), TIME_LINE_SWITCHER_DELETE_TAG) {
				close()
				actions.onDelete(def.id)
			}
		}
	}
	SwitcherAction(Res.string.timeline_switcher_settings.get(), TIME_LINE_SWITCHER_SETTINGS_TAG) {
		close()
		actions.onSettings()
	}
}

@Composable
private fun SwitcherRow(
	name: String,
	count: Int,
	selected: Boolean,
	tag: String,
	color: Color? = null,
	onClick: () -> Unit,
) {
	DropdownMenuItem(
		text = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.L),
			) {
				Text(
					text = name,
					style = MaterialTheme.typography.bodyMedium,
					color = color ?: if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
					fontWeight = if (selected) FontWeight.Bold else null,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
				HdMonoLabel(text = count.toString(), color = color ?: MaterialTheme.colorScheme.onSurfaceVariant)
			}
		},
		onClick = onClick,
		modifier = Modifier.testTag(tag),
	)
}

@Composable
private fun SwitcherAction(label: String, tag: String, onClick: () -> Unit) {
	DropdownMenuItem(
		text = {
			Text(
				text = label,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurface,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		},
		onClick = onClick,
		modifier = Modifier.testTag(tag),
	)
}

/**
 * Over the deleted-timeline bucket: how many events it holds, with Move all to (a live timeline,
 * picked from a menu) and Delete all, which asks first.
 */
@Composable
internal fun DeletedTimelineNotice(
	state: TimeLineOverview.State,
	onMoveAll: (timelineId: String) -> Unit,
	onDeleteAll: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val count = state.orphanCount
	var picking by remember { mutableStateOf(false) }
	var confirmingDelete by remember { mutableStateOf(false) }
	Box(modifier = modifier.testTag(TIME_LINE_DELETED_NOTICE_TAG)) {
		HdNoticeStrip(
			title = pluralStringResource(Res.plurals.timeline_deleted_notice_title, count, count),
			detail = Res.string.timeline_deleted_notice_detail.get(),
			primaryLabel = Res.string.timeline_deleted_move_all.get(),
			onPrimary = { picking = true },
			secondaryLabel = Res.string.timeline_deleted_delete_all.get(),
			onSecondary = { confirmingDelete = true },
			actionsEnabled = count > 0,
		)
		// Anchors the target menu under the actions, at the strip's end.
		Box(modifier = Modifier.align(Alignment.BottomEnd)) {
			DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
				MenuSection(Res.string.timeline_switcher_section.get())
				state.timelines.forEach { summary ->
					SwitcherRow(
						name = summary.def.name,
						count = summary.eventCount,
						selected = false,
						tag = timelineMoveAllTargetTag(summary.def.id),
					) {
						picking = false
						onMoveAll(summary.def.id)
					}
				}
			}
		}
	}
	AnimatedDialog(visible = confirmingDelete, onCloseRequest = { confirmingDelete = false }) {
		HdHairlineDialogShell(
			title = pluralStringResource(Res.plurals.timeline_deleted_delete_title, count, count),
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.timeline_switcher_close.get(),
		) {
			Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
				Text(
					text = Res.string.timeline_deleted_delete_detail.get(),
					style = MaterialTheme.typography.bodyMedium,
				)
				DialogButtons(
					confirmLabel = Res.string.timeline_deleted_delete_all.get(),
					confirmTag = TIME_LINE_DELETED_DELETE_CONFIRM_TAG,
					danger = true,
					onConfirm = {
						confirmingDelete = false
						onDeleteAll()
					},
					onCancel = { requestDismiss() },
				)
			}
		}
	}
}

/** The switcher's create, rename and delete dialogs for [timeline]. */
@Composable
fun TimelineSwitcherDialogs(timeline: TimeLine) {
	val switcher by timeline.switcher.subscribeAsState()
	TimelineSwitcherDialogs(
		switcher = switcher,
		onCreate = timeline::createTimeline,
		onRename = timeline::renameTimeline,
		onConfirmDelete = timeline::confirmDeleteTimeline,
		onDismiss = timeline::dismissTimelineDialog,
	)
}

/** [switcher]'s open dialog, with the reason its last request was refused under the field. */
@Composable
fun TimelineSwitcherDialogs(
	switcher: TimelineSwitcher,
	onCreate: (name: String, mode: TimelineMode, preset: CalendarPreset) -> Unit,
	onRename: (name: String) -> Unit,
	onConfirmDelete: () -> Unit,
	onDismiss: () -> Unit,
) {
	val shown = lastOf(switcher.dialog)
	AnimatedDialog(visible = switcher.dialog != null, onCloseRequest = onDismiss) {
		val dialog = shown ?: return@AnimatedDialog
		val error = switcher.error.takeIf { switcher.dialog == dialog }
		val title = when (dialog) {
			is TimelineSwitcher.Dialog.Create -> Res.string.timeline_new_title.get()
			is TimelineSwitcher.Dialog.Rename -> Res.string.timeline_rename_title.get()
			is TimelineSwitcher.Dialog.Delete -> Res.string.timeline_delete_title.get(dialog.name)
		}
		HdHairlineDialogShell(
			title = title,
			onClose = { requestDismiss() },
			closeContentDescription = Res.string.timeline_switcher_close.get(),
		) {
			Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
				when (dialog) {
					is TimelineSwitcher.Dialog.Create -> CreateFields(dialog, error, onCreate, onCancel = { requestDismiss() })
					is TimelineSwitcher.Dialog.Rename -> RenameFields(dialog, error, onRename, onCancel = { requestDismiss() })
					is TimelineSwitcher.Dialog.Delete -> DeleteFields(dialog, error, onConfirmDelete, onCancel = { requestDismiss() })
				}
			}
		}
	}
}

@Composable
private fun CreateFields(
	dialog: TimelineSwitcher.Dialog.Create,
	error: TimeLineEventError?,
	onCreate: (String, TimelineMode, CalendarPreset) -> Unit,
	onCancel: () -> Unit,
) {
	var name by remember(dialog) { mutableStateOf(dialog.name) }
	var mode by remember(dialog) { mutableStateOf(dialog.mode) }
	var preset by remember(dialog) { mutableStateOf(dialog.preset) }
	HdHairlineField(
		label = Res.string.timeline_name_label.get(),
		value = name,
		onValueChange = { name = it },
		error = errorText(error),
		testTag = TIME_LINE_NEW_NAME_TAG,
	)
	HdHairlineSegmentedPicker(
		options = Modes,
		selected = mode,
		onSelect = { mode = it },
		label = { it.label() },
		title = Res.string.timeline_settings_mode_label.get(),
		modifier = Modifier.testTag(TIME_LINE_NEW_MODE_TAG),
	)
	if (mode == TimelineMode.DATED) {
		HdHairlineSegmentedPicker(
			options = Presets,
			selected = preset,
			onSelect = { preset = it },
			label = { it.label() },
			title = Res.string.timeline_settings_preset_label.get(),
			modifier = Modifier.testTag(TIME_LINE_NEW_PRESET_TAG),
		)
	}
	DialogButtons(
		confirmLabel = Res.string.timeline_new_confirm.get(),
		confirmTag = TIME_LINE_NEW_CONFIRM_TAG,
		confirmEnabled = name.isNotBlank(),
		onConfirm = { onCreate(name, mode, preset) },
		onCancel = onCancel,
	)
}

@Composable
private fun RenameFields(
	dialog: TimelineSwitcher.Dialog.Rename,
	error: TimeLineEventError?,
	onRename: (String) -> Unit,
	onCancel: () -> Unit,
) {
	var name by remember(dialog) { mutableStateOf(dialog.name) }
	HdHairlineField(
		label = Res.string.timeline_name_label.get(),
		value = name,
		onValueChange = { name = it },
		error = errorText(error),
		testTag = TIME_LINE_RENAME_NAME_TAG,
	)
	DialogButtons(
		confirmLabel = Res.string.timeline_rename_confirm.get(),
		confirmTag = TIME_LINE_RENAME_CONFIRM_TAG,
		confirmEnabled = name.isNotBlank(),
		onConfirm = { onRename(name) },
		onCancel = onCancel,
	)
}

@Composable
private fun DeleteFields(
	dialog: TimelineSwitcher.Dialog.Delete,
	error: TimeLineEventError?,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
) {
	Text(
		text = pluralStringResource(Res.plurals.timeline_delete_detail, dialog.eventCount, dialog.eventCount),
		style = MaterialTheme.typography.bodyMedium,
	)
	errorText(error)?.let {
		HdMonoLabel(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(TIME_LINE_SWITCHER_ERROR_TAG))
	}
	DialogButtons(
		confirmLabel = Res.string.timeline_delete_confirm.get(),
		confirmTag = TIME_LINE_DELETE_CONFIRM_TAG,
		danger = true,
		onConfirm = onConfirm,
		onCancel = onCancel,
	)
}

@Composable
private fun DialogButtons(
	confirmLabel: String,
	confirmTag: String,
	onConfirm: () -> Unit,
	onCancel: () -> Unit,
	confirmEnabled: Boolean = true,
	danger: Boolean = false,
) {
	Row(horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
		Spacer(modifier = Modifier.weight(1f))
		HdHairlineButton(label = Res.string.timeline_switcher_cancel.get(), onClick = onCancel)
		HdHairlineButton(
			label = confirmLabel,
			onClick = onConfirm,
			emphasised = !danger,
			danger = danger,
			enabled = confirmEnabled,
			modifier = Modifier.testTag(confirmTag),
		)
	}
}

/** [error] as the toast would say it; null while there is none, or until it is read. */
@Composable
internal fun errorText(error: TimeLineEventError?): String? {
	if (error == null || error.isSuccess) return null
	val strRes = rememberStrRes()
	var message by remember(error) { mutableStateOf<String?>(null) }
	LaunchedEffect(error) { message = error.toastText(strRes) }
	return message
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
