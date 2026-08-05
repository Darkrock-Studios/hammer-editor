package com.darkrockstudios.apps.hammer.common.projectselection

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.compose.AnimatedDialogContainer
import com.darkrockstudios.apps.hammer.common.compose.FormField
import com.darkrockstudios.apps.hammer.common.compose.NameKind
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberNameValidation
import com.darkrockstudios.apps.hammer.common.compose.designsystem.*
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.darkrockstudios.apps.hammer.common.data.ImportFormat
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.MarkdownSplitStrategy
import com.darkrockstudios.apps.hammer.common.data.RtfSplitStrategy
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.importer.LARGE_SCENE_WORD_COUNT
import com.darkrockstudios.apps.hammer.common.data.importer.PreviewItem
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator
import org.jetbrains.compose.resources.StringResource
import com.darkrockstudios.apps.hammer.common.compose.resources.get

private val DialogMaxWidth = 560.dp
private val DialogMaxHeight = 720.dp

@Composable
fun ImportStoryDialog(
	visible: Boolean,
	projectName: String,
	options: ImportOptions,
	preview: ImportPreview,
	isParsing: Boolean,
	onProjectNameChange: (String) -> Unit,
	onCancel: () -> Unit,
	onOptionsChange: (ImportOptions) -> Unit,
	onConfirm: () -> Unit,
) {
	var renderInternal by remember { mutableStateOf(visible) }
	LaunchedEffect(visible) { if (visible) renderInternal = true }
	if (!renderInternal) return

	AnimatedDialogContainer(
		isOpen = visible,
		onDismissRequest = onCancel,
		onClosed = { renderInternal = false },
		properties = DialogProperties(usePlatformDefaultWidth = false),
	) {
		Box(modifier = Modifier.predictiveBackTransform()) {
			ImportStoryContent(
				projectName = projectName,
				options = options,
				preview = preview,
				isParsing = isParsing,
				onProjectNameChange = onProjectNameChange,
				onCancel = onCancel,
				onOptionsChange = onOptionsChange,
				onConfirm = onConfirm,
			)
		}
	}
}

@Composable
internal fun ImportStoryContent(
	projectName: String,
	options: ImportOptions,
	preview: ImportPreview,
	isParsing: Boolean,
	onProjectNameChange: (String) -> Unit,
	onCancel: () -> Unit,
	onOptionsChange: (ImportOptions) -> Unit,
	onConfirm: () -> Unit,
) {
	val nameValidation = rememberNameValidation(projectName, NameKind.Project)

	Surface(
		modifier = Modifier
			.padding(Ui.Padding.M)
			.widthIn(max = DialogMaxWidth)
			.heightIn(max = DialogMaxHeight)
			.fillMaxWidth()
			.fillMaxHeight(0.9f),
		shape = RectangleShape,
		color = MaterialTheme.colorScheme.surface,
		contentColor = MaterialTheme.colorScheme.onSurface,
		border = BorderStroke(
			width = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		),
	) {
		Column {
			ImportMasthead(
				options = options,
				preview = preview,
				isParsing = isParsing,
				onClose = onCancel,
			)
			HdFolioDivider()

			Column(
				modifier = Modifier
					.weight(1f)
					.fillMaxWidth()
					.padding(
						start = Ui.Padding.XL,
						end = Ui.Padding.XL,
						top = Ui.Padding.XL,
						bottom = Ui.Padding.XL,
					),
			) {
				Text(
					text = Res.string.project_home_import_dialog_title.get(),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onSurface,
					modifier = Modifier.padding(bottom = Ui.Padding.L),
				)

				FormField(
					value = projectName,
					onValueChange = onProjectNameChange,
					label = Res.string.create_project_heading.get(),
					autoFocus = true,
					error = nameValidation.fieldError(projectName),
				)

				Spacer(modifier = Modifier.height(Ui.Padding.XL))

				when (options.format) {
					ImportFormat.Markdown -> {
						HdHairlineSegmentedPicker(
							title = Res.string.project_home_import_heading_label.get(),
							options = MarkdownSplitStrategy.entries,
							selected = options.markdownSplitStrategy,
							onSelect = { onOptionsChange(options.copy(markdownSplitStrategy = it)) },
							label = { (it.labelRes()).get() },
						)
						if (options.markdownSplitStrategy == MarkdownSplitStrategy.Pattern) {
							Spacer(modifier = Modifier.height(Ui.Padding.L))
							ChapterPatternField(
								pattern = options.markdownChapterPattern,
								onPatternChange = { onOptionsChange(options.copy(markdownChapterPattern = it)) },
							)
						}
					}

					ImportFormat.Rtf -> {
						HdHairlineSegmentedPicker(
							title = Res.string.project_home_import_split_label.get(),
							options = RtfSplitStrategy.entries,
							selected = options.rtfSplitStrategy,
							onSelect = { onOptionsChange(options.copy(rtfSplitStrategy = it)) },
							label = { (it.labelRes()).get() },
						)
						if (options.rtfSplitStrategy == RtfSplitStrategy.Pattern) {
							Spacer(modifier = Modifier.height(Ui.Padding.L))
							ChapterPatternField(
								pattern = options.rtfChapterPattern,
								onPatternChange = { onOptionsChange(options.copy(rtfChapterPattern = it)) },
							)
						}
					}
				}

				Spacer(modifier = Modifier.height(Ui.Padding.XL))

				HdHairlineToggleRow(
					checked = options.createChapterGroups,
					onCheckedChange = { onOptionsChange(options.copy(createChapterGroups = it)) },
					label = Res.string.project_home_import_create_groups_label.get(),
				)

				Spacer(modifier = Modifier.height(Ui.Padding.XL))

				ImportPreviewPane(preview, isParsing, Modifier.weight(1f))
			}

			ImportFooter(
				onCancel = onCancel,
				onConfirm = onConfirm,
				confirmEnabled = !isParsing && !preview.isEmpty && nameValidation.isValid,
			)
		}
	}
}

@Composable
private fun ChapterPatternField(pattern: String, onPatternChange: (String) -> Unit) {
	FormField(
		value = pattern,
		onValueChange = onPatternChange,
		label = Res.string.project_home_import_pattern_label.get(),
	)
}

@Composable
private fun ImportMasthead(
	options: ImportOptions,
	preview: ImportPreview,
	isParsing: Boolean,
	onClose: () -> Unit,
) {
	val meta = remember(options, preview.totalScenes, preview.isEmpty, isParsing) {
		buildList {
			add(options.format.metaLabel())
			when (options.format) {
				ImportFormat.Markdown -> add(options.markdownSplitStrategy.name.uppercase())
				ImportFormat.Rtf -> add(options.rtfSplitStrategy.name.uppercase())
			}
			if (isParsing) {
				add("READING")
			} else if (!preview.isEmpty) {
				add("${preview.totalScenes} SCENES")
			}
		}
	}
	HdMasthead(
		section = "IMPORT",
		leadingMeta = meta,
		trailing = { HdMastheadAction(label = "× CLOSE", onClick = onClose) },
	)
}

@Composable
private fun ImportFooter(
	onCancel: () -> Unit,
	onConfirm: () -> Unit,
	confirmEnabled: Boolean,
) {
	HorizontalDivider(
		thickness = Dp.Hairline,
		color = MaterialTheme.colorScheme.outlineVariant,
	)
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surfaceContainerLow)
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.L),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		HdMonoLabel(
			text = "ESC CANCEL",
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Spacer(modifier = Modifier.weight(1f))
		HdHairlineButton(
			label = Res.string.project_home_import_cancel.get(),
			onClick = onCancel,
		)
		HdHairlineButton(
			label = Res.string.project_home_import_execute.get(),
			onClick = onConfirm,
			emphasised = true,
			enabled = confirmEnabled,
		)
	}
}

@Composable
private fun ImportPreviewPane(
	preview: ImportPreview,
	isParsing: Boolean,
	modifier: Modifier = Modifier,
) {
	Column(modifier) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically,
		) {
			HdMonoLabel(Res.string.project_home_import_preview_label.get())
			if (!isParsing && !preview.isEmpty) {
				HdMonoLabel(
					Res.string.project_home_import_preview_count.get(
						preview.totalScenes,
					),
				)
			}
		}
		Spacer(modifier = Modifier.height(Ui.Padding.S))
		if (!isParsing) {
			LargeSceneWarning(remember(preview) { preview.oversizedScenes })
		}
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.border(
					width = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
					shape = RectangleShape,
				),
		) {
			if (isParsing) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(Ui.Padding.L),
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M, Alignment.CenterHorizontally),
					verticalAlignment = Alignment.CenterVertically,
				) {
					CircularProgressIndicator(
						modifier = Modifier.size(18.dp),
						strokeWidth = 2.dp,
						color = MaterialTheme.colorScheme.primary,
					)
					HdMonoLabel(Res.string.project_home_import_preview_reading.get())
				}
			} else if (preview.isEmpty) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(Ui.Padding.L),
					contentAlignment = Alignment.Center,
				) {
					HdMonoLabel(Res.string.project_home_import_preview_empty.get())
				}
			} else {
				// A manuscript can split into thousands of scenes; only compose the visible rows.
				val rows = remember(preview) { preview.toRows() }
				LazyColumn(contentPadding = PaddingValues(Ui.Padding.M)) {
					items(rows) { row ->
						when (row) {
							is PreviewRow.Group -> PreviewGroupRow(row.name)
							is PreviewRow.Scene -> PreviewSceneRow(row)
						}
					}
				}
			}
		}
	}
}

/** Amber notice when the import would produce a scene over [LARGE_SCENE_WORD_COUNT]. Never blocks the import. */
@Composable
private fun LargeSceneWarning(oversized: List<PreviewItem.Scene>) {
	if (oversized.isEmpty()) return

	val message = if (oversized.size == 1) {
		Res.string.project_home_import_large_scene_one.get(
			oversized.first().wordCount.formatDecimalSeparator(),
		)
	} else {
		Res.string.project_home_import_large_scene_many.get(
			oversized.size,
			oversized.minOf { it.wordCount }.formatDecimalSeparator(),
		)
	}

	HdWarningNotice(
		label = Res.string.project_home_import_large_scene_label.get(),
		message = message,
	)
	Spacer(modifier = Modifier.height(Ui.Padding.S))
}

/** One flat row of the preview tree: a group header, or a scene at top level or inside a group. */
private sealed interface PreviewRow {
	class Group(val name: String) : PreviewRow
	class Scene(val item: PreviewItem.Scene, val indented: Boolean) : PreviewRow
}

private fun ImportPreview.toRows(): List<PreviewRow> = buildList {
	items.forEach { item ->
		when (item) {
			is PreviewItem.Scene -> add(PreviewRow.Scene(item, indented = false))
			is PreviewItem.Group -> {
				add(PreviewRow.Group(item.name))
				item.scenes.forEach { add(PreviewRow.Scene(it, indented = true)) }
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
			tint = MaterialTheme.colorScheme.onSurface,
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
private fun PreviewSceneRow(row: PreviewRow.Scene) {
	val oversized = row.item.isOversized
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = if (row.indented) Ui.Padding.L else 0.dp, top = 2.dp, bottom = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		val amber = LocalHammerColors.current.warning
		Icon(
			Icons.AutoMirrored.Filled.Article,
			contentDescription = null,
			tint = if (oversized) amber else MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.size(18.dp),
		)
		Spacer(modifier = Modifier.width(Ui.Padding.S))
		Text(
			row.item.name,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurface,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f),
		)
		if (oversized) {
			Spacer(modifier = Modifier.width(Ui.Padding.S))
			HdMonoLabel(
				text = Res.string.project_home_import_scene_word_count.get(
					row.item.wordCount.formatDecimalSeparator(),
				),
				color = amber,
				maxLines = 1,
				softWrap = false,
			)
		}
	}
}

private fun ImportFormat.metaLabel(): String = when (this) {
	ImportFormat.Markdown -> "MARKDOWN"
	ImportFormat.Rtf -> "RTF"
}

private fun MarkdownSplitStrategy.labelRes(): StringResource = when (this) {
	MarkdownSplitStrategy.Auto -> Res.string.project_home_import_heading_auto
	MarkdownSplitStrategy.H1 -> Res.string.project_home_import_heading_h1
	MarkdownSplitStrategy.H2 -> Res.string.project_home_import_heading_h2
	MarkdownSplitStrategy.Pattern -> Res.string.project_home_import_split_pattern
}

private fun RtfSplitStrategy.labelRes(): StringResource = when (this) {
	RtfSplitStrategy.Formatting -> Res.string.project_home_import_split_formatting
	RtfSplitStrategy.Pattern -> Res.string.project_home_import_split_pattern
	RtfSplitStrategy.SingleScene -> Res.string.project_home_import_split_single
}
