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
import com.darkrockstudios.apps.hammer.common.compose.FormDialogScaffold
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
import org.jetbrains.compose.resources.pluralStringResource
import com.darkrockstudios.apps.hammer.common.compose.resources.get

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

	FormDialogScaffold(
		title = Res.string.project_home_import_dialog_title.get(),
		confirmLabel = Res.string.project_home_import_execute.get(),
		cancelLabel = Res.string.project_home_import_cancel.get(),
		onConfirm = onConfirm,
		onCancel = onCancel,
		confirmEnabled = !isParsing && !preview.isEmpty && nameValidation.isValid,
		modifier = Modifier
			.heightIn(max = DialogMaxHeight)
			.fillMaxHeight(0.9f),
	) {
		FormField(
			value = projectName,
			onValueChange = onProjectNameChange,
			label = Res.string.create_project_heading.get(),
			autoFocus = true,
			error = nameValidation.fieldError(projectName),
		)

		when (options.format) {
			ImportFormat.Markdown -> Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
				HdHairlineSegmentedPicker(
					title = Res.string.project_home_import_heading_label.get(),
					options = MarkdownSplitStrategy.entries,
					selected = options.markdownSplitStrategy,
					onSelect = { onOptionsChange(options.copy(markdownSplitStrategy = it)) },
					label = { (it.labelRes()).get() },
				)
				if (options.markdownSplitStrategy == MarkdownSplitStrategy.Pattern) {
					ChapterPatternField(
						pattern = options.markdownChapterPattern,
						onPatternChange = { onOptionsChange(options.copy(markdownChapterPattern = it)) },
					)
				}
			}

			ImportFormat.Rtf -> Column(verticalArrangement = Arrangement.spacedBy(Ui.Padding.L)) {
				HdHairlineSegmentedPicker(
					title = Res.string.project_home_import_split_label.get(),
					options = RtfSplitStrategy.entries,
					selected = options.rtfSplitStrategy,
					onSelect = { onOptionsChange(options.copy(rtfSplitStrategy = it)) },
					label = { (it.labelRes()).get() },
				)
				if (options.rtfSplitStrategy == RtfSplitStrategy.Pattern) {
					ChapterPatternField(
						pattern = options.rtfChapterPattern,
						onPatternChange = { onOptionsChange(options.copy(rtfChapterPattern = it)) },
					)
				}
			}
		}

		HdHairlineToggleRow(
			checked = options.createChapterGroups,
			onCheckedChange = { onOptionsChange(options.copy(createChapterGroups = it)) },
			label = Res.string.project_home_import_create_groups_label.get(),
		)

		ImportPreviewPane(preview, isParsing, Modifier.weight(1f))
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
					pluralStringResource(
						Res.plurals.project_home_import_preview_scenes,
						preview.totalScenes,
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
