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
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.project_home_export_cancel
import com.darkrockstudios.apps.hammer.project_home_export_chapters_label
import com.darkrockstudios.apps.hammer.project_home_export_close
import com.darkrockstudios.apps.hammer.project_home_export_dialog_title
import com.darkrockstudios.apps.hammer.project_home_export_execute
import com.darkrockstudios.apps.hammer.project_home_export_format_label
import com.darkrockstudios.apps.hammer.project_home_export_help_icon_description
import com.darkrockstudios.apps.hammer.project_home_export_limit_scenes_hint
import com.darkrockstudios.apps.hammer.project_home_export_limit_scenes_label
import com.darkrockstudios.apps.hammer.project_home_export_scenes_clear_all
import com.darkrockstudios.apps.hammer.project_home_export_scenes_label
import com.darkrockstudios.apps.hammer.project_home_export_scenes_select_all
import com.darkrockstudios.apps.hammer.project_home_export_scenes_selected
import com.darkrockstudios.apps.hammer.project_home_export_section

private val DialogMaxWidth = 520.dp

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
			formats = exportFormatChoices(),
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
	formats: List<ExportFormatChoice>,
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
					options = formats,
					selected = formats.firstOrNull { it.formatId == options.format } ?: formats.first(),
					onSelect = { onOptionsChanged(options.copy(format = it.formatId)) },
					label = { it.label },
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
						SceneSelector(
							entries = exportableScenes,
							selected = selectedIds,
							multiple = true,
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
