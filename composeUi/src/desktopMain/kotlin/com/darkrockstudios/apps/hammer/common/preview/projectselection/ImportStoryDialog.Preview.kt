package com.darkrockstudios.apps.hammer.common.preview.projectselection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
import com.darkrockstudios.apps.hammer.common.data.MarkdownSplitStrategy
import com.darkrockstudios.apps.hammer.common.data.importer.ImportPreview
import com.darkrockstudios.apps.hammer.common.data.importer.PreviewItem
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projectselection.ImportStoryContent

/**
 * Rendered at a deliberately short canvas height with many scenes: the preview list scrolls within
 * the bounded dialog while the option controls and footer buttons stay visible.
 */
@Preview(widthDp = 640, heightDp = 560)
@Composable
fun ImportStoryDialogPreview() {
	val preview = ImportPreview(
		items = List(14) { i -> PreviewItem.Scene(name = "Chapter ${i + 1}", markdown = "") },
	)
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
				contentAlignment = Alignment.Center,
			) {
				ImportStoryContent(
					projectName = "Alice In Wonderland 2",
					options = ImportOptions(),
					preview = preview,
					isParsing = false,
					onProjectNameChange = {},
					onCancel = {},
					onOptionsChange = {},
					onConfirm = {},
				)
			}
		}
	}
}

/**
 * Markdown import at phone width with the regex escape hatch selected: the chapter pattern field is
 * showing and the four-cell detection picker is at its tightest.
 */
@Preview(widthDp = 400, heightDp = 800)
@Composable
fun ImportStoryDialogMarkdownPatternPreview() {
	val preview = ImportPreview(
		items = List(6) { i -> PreviewItem.Scene(name = "Chapter ${i + 1}", markdown = "") },
	)
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
				contentAlignment = Alignment.Center,
			) {
				ImportStoryContent(
					projectName = "Alice In Wonderland 2",
					options = ImportOptions(markdownSplitStrategy = MarkdownSplitStrategy.Pattern),
					preview = preview,
					isParsing = false,
					onProjectNameChange = {},
					onCancel = {},
					onOptionsChange = {},
					onConfirm = {},
				)
			}
		}
	}
}

/** Chapter detection found nothing, so the whole manuscript landed in one scene. */
@Preview(widthDp = 640, heightDp = 760)
@Composable
fun ImportStoryDialogLargeScenePreview() {
	val preview = remember {
		ImportPreview(
			items = listOf(
				PreviewItem.Scene(name = "Chapter One", markdown = "A normal opening chapter."),
				PreviewItem.Scene(name = "The Long Walk Home", markdown = "word ".repeat(54_300)),
			),
		)
	}
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
				contentAlignment = Alignment.Center,
			) {
				ImportStoryContent(
					projectName = "The Long Walk Home",
					options = ImportOptions(),
					preview = preview,
					isParsing = false,
					onProjectNameChange = {},
					onCancel = {},
					onOptionsChange = {},
					onConfirm = {},
				)
			}
		}
	}
}

/** The window between the file picker closing and the parse landing. */
@Preview(widthDp = 640, heightDp = 560)
@Composable
fun ImportStoryDialogParsingPreview() {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
				contentAlignment = Alignment.Center,
			) {
				ImportStoryContent(
					projectName = "Alice In Wonderland 2",
					options = ImportOptions(),
					preview = ImportPreview(items = emptyList()),
					isParsing = true,
					onProjectNameChange = {},
					onCancel = {},
					onOptionsChange = {},
					onConfirm = {},
				)
			}
		}
	}
}
