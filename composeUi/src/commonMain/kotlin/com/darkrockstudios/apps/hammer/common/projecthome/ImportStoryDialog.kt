package com.darkrockstudios.apps.hammer.common.projecthome

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.SimpleDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.ChapterHeadingLevel
import com.darkrockstudios.apps.hammer.common.data.ImportFormat
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.importer.PreviewItem
import org.jetbrains.compose.resources.stringResource

@Composable
fun ImportStoryDialog(
	visible: Boolean,
	options: ImportOptions,
	preview: ImportPreview,
	onCancel: () -> Unit,
	onOptionsChange: (ImportOptions) -> Unit,
	onConfirm: () -> Unit,
) {
	SimpleDialog(
		visible = visible,
		onCloseRequest = onCancel,
		title = Res.string.project_home_import_dialog_title.get(),
		dismissOnTapOutside = true,
	) {
		Column(modifier = Modifier.padding(horizontal = Ui.Padding.L)) {
			Text(
				stringResource(Res.string.project_home_import_format_label),
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Spacer(modifier = Modifier.height(Ui.Padding.S))
			ImportFormatDropdown(
				selected = options.format,
				onSelected = { onOptionsChange(options.copy(format = it)) },
				modifier = Modifier.fillMaxWidth(),
			)

			Spacer(modifier = Modifier.height(Ui.Padding.XL))

			Text(
				stringResource(Res.string.project_home_import_heading_label),
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Spacer(modifier = Modifier.height(Ui.Padding.S))
			ChapterHeadingLevelSelector(
				selected = options.chapterHeadingLevel,
				onSelected = { onOptionsChange(options.copy(chapterHeadingLevel = it)) },
			)

			Spacer(modifier = Modifier.height(Ui.Padding.XL))

			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Switch(
					checked = options.createChapterGroups,
					onCheckedChange = { onOptionsChange(options.copy(createChapterGroups = it)) },
				)
				Spacer(modifier = Modifier.width(Ui.Padding.M))
				Text(
					stringResource(Res.string.project_home_import_create_groups_label),
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface,
				)
			}

			Spacer(modifier = Modifier.height(Ui.Padding.XL))

			Text(
				stringResource(Res.string.project_home_import_preview_label),
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Spacer(modifier = Modifier.height(Ui.Padding.S))
			ImportPreviewPane(preview)

			Spacer(modifier = Modifier.height(Ui.Padding.XL))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End,
			) {
				TextButton(onClick = onCancel) {
					Text(stringResource(Res.string.project_home_import_cancel))
				}
				Spacer(modifier = Modifier.width(Ui.Padding.M))
				Button(
					onClick = onConfirm,
					enabled = !preview.isEmpty,
				) {
					Text(stringResource(Res.string.project_home_import_execute))
				}
			}
		}
	}
}

@Composable
private fun ImportPreviewPane(preview: ImportPreview) {
	Surface(
		modifier = Modifier.fillMaxWidth(),
		tonalElevation = 1.dp,
		color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
	) {
		Column(
			modifier = Modifier
				.heightIn(max = 240.dp)
				.verticalScroll(rememberScrollState())
				.padding(Ui.Padding.M),
		) {
			if (preview.isEmpty) {
				Text(
					stringResource(Res.string.project_home_import_preview_empty),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			} else {
				Text(
					stringResource(Res.string.project_home_import_preview_count, preview.totalScenes),
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Spacer(modifier = Modifier.height(Ui.Padding.S))
				preview.items.forEach { item ->
					when (item) {
						is PreviewItem.Scene -> PreviewSceneRow(item.name, indented = false)
						is PreviewItem.Group -> {
							PreviewGroupRow(item.name)
							item.scenes.forEach { childScene ->
								PreviewSceneRow(childScene.name, indented = true)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun PreviewGroupRow(name: String) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			Icons.Default.Folder,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.primary,
			modifier = Modifier.size(18.dp),
		)
		Spacer(modifier = Modifier.width(Ui.Padding.S))
		Text(
			name,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@Composable
private fun PreviewSceneRow(name: String, indented: Boolean) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = if (indented) Ui.Padding.L else 0.dp, top = 2.dp, bottom = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			Icons.AutoMirrored.Filled.Article,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(18.dp),
		)
		Spacer(modifier = Modifier.width(Ui.Padding.S))
		Text(
			name,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportFormatDropdown(
	selected: ImportFormat,
	onSelected: (ImportFormat) -> Unit,
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
					onSelected(ImportFormat.Markdown)
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
private fun formatLabel(format: ImportFormat): String = when (format) {
	ImportFormat.Markdown -> stringResource(Res.string.project_home_export_format_markdown)
}

@Composable
private fun ChapterHeadingLevelSelector(
	selected: ChapterHeadingLevel,
	onSelected: (ChapterHeadingLevel) -> Unit,
) {
	val levels = remember { ChapterHeadingLevel.entries }
	SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
		levels.forEachIndexed { index, level ->
			SegmentedButton(
				selected = level == selected,
				onClick = { onSelected(level) },
				shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size),
			) {
				Text(
					when (level) {
						ChapterHeadingLevel.H1 -> stringResource(Res.string.project_home_import_heading_h1)
						ChapterHeadingLevel.H2 -> stringResource(Res.string.project_home_import_heading_h2)
					}
				)
			}
		}
	}
}
