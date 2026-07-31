package com.darkrockstudios.apps.hammer.common.preview.projectselection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ImportOptions
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
