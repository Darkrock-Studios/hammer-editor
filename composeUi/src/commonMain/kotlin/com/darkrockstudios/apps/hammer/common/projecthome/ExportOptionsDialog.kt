package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdButtonBar
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdFolioDivider
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineCheckbox
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineDropdown
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineToggleRow
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHelpButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMasthead
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMastheadAction
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPickerList
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdPickerRow
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ExportFormat
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.project_home_export_cancel
import com.darkrockstudios.apps.hammer.project_home_export_chapters_label
import com.darkrockstudios.apps.hammer.project_home_export_close
import com.darkrockstudios.apps.hammer.project_home_export_dialog_title
import com.darkrockstudios.apps.hammer.project_home_export_execute
import com.darkrockstudios.apps.hammer.project_home_export_format_docx
import com.darkrockstudios.apps.hammer.project_home_export_format_epub
import com.darkrockstudios.apps.hammer.project_home_export_format_label
import com.darkrockstudios.apps.hammer.project_home_export_format_markdown
import com.darkrockstudios.apps.hammer.project_home_export_format_pdf
import com.darkrockstudios.apps.hammer.project_home_export_format_rtf
import com.darkrockstudios.apps.hammer.project_home_export_help_icon_description
import com.darkrockstudios.apps.hammer.project_home_export_limit_scenes_hint
import com.darkrockstudios.apps.hammer.project_home_export_limit_scenes_label
import com.darkrockstudios.apps.hammer.project_home_export_scenes_clear_all
import com.darkrockstudios.apps.hammer.project_home_export_scenes_label
import com.darkrockstudios.apps.hammer.project_home_export_scenes_select_all
import com.darkrockstudios.apps.hammer.project_home_export_scenes_selected
import com.darkrockstudios.apps.hammer.project_home_export_section
import org.jetbrains.compose.resources.StringResource

private val DialogMaxWidth = 520.dp

internal fun exportSceneRowTag(id: Int) = "export-scene-row-$id"
internal fun exportGroupRowTag(id: Int) = "export-group-row-$id"
internal const val EXPORT_SCENE_MASTER_TOGGLE_TAG = "export-scene-master-toggle"

/**
 * Fully controlled: all option state lives in the component's retained state so
 * in-dialog edits survive configuration changes (see issue #885).
 */
@Composable
fun ExportOptionsDialog(
	visible: Boolean,
	options: ExportOptions,
	exportableScenes: List<ExportableScene>,
	onOptionsChanged: (ExportOptions) -> Unit,
	onCancel: () -> Unit,
	onConfirm: (ExportOptions) -> Unit,
	onDismissed: () -> Unit = {},
	working: Boolean = false,
) {
	var showHelp by remember { mutableStateOf(false) }

	AnimatedDialog(
		visible = visible,
		onCloseRequest = { if (!working) onCancel() },
		dismissOnTapOutside = !working,
		onDismissed = onDismissed,
	) {
		ExportOptionsDialogContent(
			options = options,
			exportableScenes = exportableScenes,
			onOptionsChanged = onOptionsChanged,
			onCancel = onCancel,
			onConfirm = onConfirm,
			onShowHelp = { showHelp = true },
			working = working,
		)
	}

	if (showHelp) {
		ExportHelpDialog(onDismiss = { showHelp = false })
	}
}

@Composable
internal fun ExportOptionsDialogContent(
	options: ExportOptions,
	exportableScenes: List<ExportableScene>,
	onOptionsChanged: (ExportOptions) -> Unit,
	onCancel: () -> Unit,
	onConfirm: (ExportOptions) -> Unit,
	onShowHelp: () -> Unit,
	working: Boolean = false,
) {
	val allSceneIds = remember(exportableScenes) { allSceneIds(exportableScenes) }

	Surface(
		modifier = Modifier
			.padding(Ui.Padding.M)
			.widthIn(max = DialogMaxWidth)
			.fillMaxWidth(),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(
			width = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		),
	) {
		Column {
			HdMasthead(
				section = Res.string.project_home_export_section.get(),
				trailing = {
					HdHelpButton(
						onClick = onShowHelp,
						contentDescription = Res.string.project_home_export_help_icon_description.get(),
					)
					HdMastheadAction(
						label = Res.string.project_home_export_close.get(),
						onClick = { if (!working) onCancel() },
					)
				},
			)
			HdFolioDivider()

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(
						start = Ui.Padding.XL,
						end = Ui.Padding.XL,
						top = Ui.Padding.L,
						bottom = Ui.Padding.M,
					),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = Res.string.project_home_export_dialog_title.get(),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface,
				)
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Column(
				modifier = Modifier
					// Short screens and large font scales must scroll the options rather
					// than push the button bar off-screen.
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState())
					.padding(
						horizontal = Ui.Padding.XL,
						vertical = Ui.Padding.XL,
					),
				verticalArrangement = Arrangement.spacedBy(Ui.Padding.XL),
			) {
				HdHairlineToggleRow(
					checked = options.treatTopLevelAsChapters,
					onCheckedChange = { onOptionsChanged(options.copy(treatTopLevelAsChapters = it)) },
					label = Res.string.project_home_export_chapters_label.get(),
				)

				HdHairlineDropdown(
					title = Res.string.project_home_export_format_label.get(),
					options = AVAILABLE_EXPORT_FORMATS,
					selected = options.format,
					onSelect = { onOptionsChanged(options.copy(format = it)) },
					label = { (it.labelRes()).get() },
				)

				if (allSceneIds.isNotEmpty()) {
					HdHairlineToggleRow(
						checked = options.sceneIds != null,
						onCheckedChange = { limit ->
							onOptionsChanged(options.copy(sceneIds = if (limit) emptySet() else null))
						},
						label = Res.string.project_home_export_limit_scenes_label.get(),
						hint = Res.string.project_home_export_limit_scenes_hint.get(),
					)

					val selectedIds = options.sceneIds
					if (selectedIds != null) {
						ExportSceneSelector(
							entries = exportableScenes,
							allIds = allSceneIds,
							selected = selectedIds,
							onSelectionChanged = { onOptionsChanged(options.copy(sceneIds = it)) },
						)
					}
				}
			}

			Spacer(modifier = Modifier.height(Ui.Padding.M))

			HdButtonBar(
				cancelLabel = Res.string.project_home_export_cancel.get(),
				primaryLabel = Res.string.project_home_export_execute.get(),
				onCancel = onCancel,
				onPrimary = {
					// Selecting every scene means no limit at all: exporting the full story
					// keeps empty chapter groups instead of silently dropping them.
					val selectedIds = options.sceneIds
					val confirmed = if (selectedIds != null && isFullSelection(exportableScenes, selectedIds)) {
						options.copy(sceneIds = null)
					} else {
						options
					}
					onConfirm(confirmed)
				},
				primaryLoading = working,
				primaryEnabled = options.sceneIds?.isNotEmpty() ?: true,
				cancelEnabled = !working,
			)
		}
	}
}

@Composable
private fun ExportSceneSelector(
	entries: List<ExportableScene>,
	allIds: Set<Int>,
	selected: Set<Int>,
	onSelectionChanged: (Set<Int>) -> Unit,
) {
	val allSelected = allIds.isNotEmpty() && selected.containsAll(allIds)

	Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.M)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
		) {
			HdMonoLabel(text = Res.string.project_home_export_scenes_label.get())
			Text(
				text = Res.string.project_home_export_scenes_selected.get(selected.size, allIds.size),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.weight(1f),
			)
			HdHairlineButton(
				label = if (allSelected) {
					Res.string.project_home_export_scenes_clear_all.get()
				} else {
					Res.string.project_home_export_scenes_select_all.get()
				},
				onClick = { onSelectionChanged(if (allSelected) emptySet() else allIds) },
				modifier = Modifier.testTag(EXPORT_SCENE_MASTER_TOGGLE_TAG),
			)
		}

		HdPickerList {
			items(items = entries, key = { it.id }) { entry ->
				if (entry.isGroup) {
					ExportGroupRow(
						entry = entry,
						fullySelected = isGroupFullySelected(entries, selected, entry),
						hasScenes = descendantSceneIds(entries, entry).isNotEmpty(),
						onToggle = { onSelectionChanged(toggleGroup(entries, selected, entry)) },
					)
				} else {
					ExportSceneRow(
						entry = entry,
						isSelected = entry.id in selected,
						onToggle = { onSelectionChanged(toggleScene(selected, entry.id)) },
					)
				}
			}
		}
	}
}

@Composable
private fun ExportGroupRow(
	entry: ExportableScene,
	fullySelected: Boolean,
	hasScenes: Boolean,
	onToggle: () -> Unit,
) {
	val interaction = if (hasScenes) {
		Modifier.toggleable(
			value = fullySelected,
			role = Role.Checkbox,
			onValueChange = { onToggle() },
		)
	} else {
		// Nothing beneath it to select; an inert checkbox would just mislead.
		Modifier
	}
	HdPickerRow(
		label = entry.name,
		depth = entry.depth,
		icon = Icons.Filled.Folder,
		modifier = interaction.testTag(exportGroupRowTag(entry.id)),
		trailing = { if (hasScenes) HdHairlineCheckbox(checked = fullySelected) },
	)
}

@Composable
private fun ExportSceneRow(
	entry: ExportableScene,
	isSelected: Boolean,
	onToggle: () -> Unit,
) {
	HdPickerRow(
		label = entry.name,
		depth = entry.depth,
		modifier = Modifier
			.toggleable(
				value = isSelected,
				role = Role.Checkbox,
				onValueChange = { onToggle() },
			)
			.testTag(exportSceneRowTag(entry.id)),
		trailing = { HdHairlineCheckbox(checked = isSelected) },
	)
}

private val AVAILABLE_EXPORT_FORMATS =
	listOf(
		ExportFormat.Epub,
		ExportFormat.Docx,
		ExportFormat.Rtf,
		ExportFormat.Pdf,
		ExportFormat.Markdown,
	)

private fun ExportFormat.labelRes(): StringResource = when (this) {
	ExportFormat.Markdown -> Res.string.project_home_export_format_markdown
	ExportFormat.Epub -> Res.string.project_home_export_format_epub
	ExportFormat.Pdf -> Res.string.project_home_export_format_pdf
	ExportFormat.Docx -> Res.string.project_home_export_format_docx
	ExportFormat.Rtf -> Res.string.project_home_export_format_rtf
}
