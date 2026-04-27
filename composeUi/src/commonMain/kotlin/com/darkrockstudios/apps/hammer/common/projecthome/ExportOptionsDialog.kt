package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ExportFormat
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExportOptionsDialog(
	visible: Boolean,
	initialOptions: ExportOptions,
	onCancel: () -> Unit,
	onConfirm: (ExportOptions) -> Unit,
) {
	var treatAsChapters by remember(initialOptions, visible) {
		mutableStateOf(initialOptions.treatTopLevelAsChapters)
	}
	var format by remember(initialOptions, visible) {
		mutableStateOf(initialOptions.format)
	}

	SimpleDialog(
		visible = visible,
		onCloseRequest = onCancel,
		title = Res.string.project_home_export_dialog_title.get(),
		dismissOnTapOutside = true,
	) {
		Column(modifier = Modifier.padding(horizontal = Ui.Padding.L)) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Switch(
					checked = treatAsChapters,
					onCheckedChange = { treatAsChapters = it },
				)
				Spacer(modifier = Modifier.width(Ui.Padding.M))
				Text(
					stringResource(Res.string.project_home_export_chapters_label),
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface,
				)
			}

			Spacer(modifier = Modifier.height(Ui.Padding.XL))

			Text(
				stringResource(Res.string.project_home_export_format_label),
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Spacer(modifier = Modifier.height(Ui.Padding.S))
			ExportFormatDropdown(
				selected = format,
				onSelected = { format = it },
				modifier = Modifier.fillMaxWidth(),
			)

			Spacer(modifier = Modifier.height(Ui.Padding.XL))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End,
			) {
				TextButton(onClick = onCancel) {
					Text(stringResource(Res.string.project_home_export_cancel))
				}
				Spacer(modifier = Modifier.width(Ui.Padding.M))
				Button(onClick = {
					onConfirm(
						ExportOptions(
							treatTopLevelAsChapters = treatAsChapters,
							format = format,
						)
					)
				}) {
					Text(stringResource(Res.string.project_home_export_execute))
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatDropdown(
	selected: ExportFormat,
	onSelected: (ExportFormat) -> Unit,
	modifier: Modifier = Modifier,
) {
	var expanded by remember { mutableStateOf(false) }

	ExposedDropdownMenuBox(
		expanded = expanded,
		onExpandedChange = { expanded = it },
		modifier = modifier,
	) {
		TextField(
			value = formatLabel(selected),
			onValueChange = {},
			readOnly = true,
			singleLine = true,
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
			modifier = Modifier
				.fillMaxWidth()
				.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
		)

		ExposedDropdownMenu(
			expanded = expanded,
			onDismissRequest = { expanded = false },
		) {
			DropdownMenuItem(
				text = { Text(stringResource(Res.string.project_home_export_format_markdown)) },
				onClick = {
					onSelected(ExportFormat.Markdown)
					expanded = false
				},
			)
			DropdownMenuItem(
				text = { Text(stringResource(Res.string.project_home_export_format_epub)) },
				onClick = { },
				enabled = false,
			)
		}
	}
}

@Composable
private fun formatLabel(format: ExportFormat): String = when (format) {
	ExportFormat.Markdown -> stringResource(Res.string.project_home_export_format_markdown)
	ExportFormat.Epub -> stringResource(Res.string.project_home_export_format_epub)
}
