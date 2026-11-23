package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.projectselection.settings.MergeMode
import com.darkrockstudios.apps.hammer.common.projectselection.settings.MergeProjectsDialogContent
import com.darkrockstudios.apps.hammer.common.projectselection.settings.MergeReplaceConfirmCard

@Preview(widthDp = 560, heightDp = 440)
@Composable
fun MergeProjectsDialogMergePreview() = MergeProjectsDialogPreview(MergeMode.Merge)

@Preview(widthDp = 560, heightDp = 440)
@Composable
fun MergeProjectsDialogReplacePreview() = MergeProjectsDialogPreview(MergeMode.Replace)

@Composable
private fun MergeProjectsDialogPreview(mode: MergeMode) {
	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, true) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
				contentAlignment = Alignment.Center,
			) {
				MergeProjectsDialogContent(
					mode = mode,
					onModeChange = {},
					onCancel = {},
					onContinue = {},
				)
			}
		}
	}
}

/** The merge prompt with [mode] chosen, over a scrim in the surrounding theme. */
@Composable
internal fun MergeProjectsScrimPreview(mode: MergeMode) {
	PreviewScrim {
		MergeProjectsDialogContent(mode = mode, onModeChange = {}, onCancel = {}, onContinue = {})
	}
}

/** The confirm Replace raises before deleting every local project. */
@Composable
internal fun MergeReplaceConfirmScrimPreview() {
	PreviewScrim {
		MergeReplaceConfirmCard(onConfirm = {}, onCancel = {})
	}
}
