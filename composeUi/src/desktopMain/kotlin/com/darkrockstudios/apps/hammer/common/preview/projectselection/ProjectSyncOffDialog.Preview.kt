package com.darkrockstudios.apps.hammer.common.preview.projectselection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectData
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSyncOffContent

@Preview(widthDp = 600, heightDp = 400)
@Composable
fun ProjectSyncOffDialogPreview() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		ProjectSyncOffContent(
			projectName = fakeProjectData().definition.name,
			onConfirm = {},
			onCancel = {},
		)
	}
}
