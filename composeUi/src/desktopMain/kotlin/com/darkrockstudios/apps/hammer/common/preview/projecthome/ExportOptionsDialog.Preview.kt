package com.darkrockstudios.apps.hammer.common.preview.projecthome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.ExportOptions
import com.darkrockstudios.apps.hammer.common.data.ExportableScene
import com.darkrockstudios.apps.hammer.common.preview.KoinApplicationPreview
import com.darkrockstudios.apps.hammer.common.preview.globalSettingsPreview
import com.darkrockstudios.apps.hammer.common.projecthome.ExportOptionsDialogContent

private val previewScenes = listOf(
	ExportableScene(id = 1, name = "Prologue", isGroup = false, depth = 0),
	ExportableScene(id = 2, name = "Chapter 1: The Door", isGroup = true, depth = 0),
	ExportableScene(id = 3, name = "The Door Opens", isGroup = false, depth = 1),
	ExportableScene(id = 4, name = "The Discovery", isGroup = false, depth = 1),
	ExportableScene(id = 5, name = "Interludes", isGroup = true, depth = 1),
	ExportableScene(id = 6, name = "A Quiet Moment", isGroup = false, depth = 2),
	ExportableScene(id = 7, name = "Epilogue", isGroup = false, depth = 0),
)

@Composable
private fun ExportOptionsPreviewFrame(options: ExportOptions) {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
				contentAlignment = Alignment.Center,
			) {
				ExportOptionsDialogContent(
					options = options,
					exportableScenes = previewScenes,
					onOptionsChanged = {},
					onCancel = {},
					onConfirm = {},
					onShowHelp = {},
				)
			}
		}
	}
}

@Preview(widthDp = 600, heightDp = 480)
@Composable
fun ExportOptionsDialogPreview() {
	ExportOptionsPreviewFrame(ExportOptions())
}

@Preview(widthDp = 600, heightDp = 760)
@Composable
fun ExportOptionsDialogLimitScenesPreview() {
	ExportOptionsPreviewFrame(ExportOptions(sceneIds = setOf(3, 4, 6)))
}
