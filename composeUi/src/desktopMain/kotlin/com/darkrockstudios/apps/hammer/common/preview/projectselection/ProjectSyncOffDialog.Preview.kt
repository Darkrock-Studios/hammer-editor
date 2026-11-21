package com.darkrockstudios.apps.hammer.common.preview.projectselection

import com.darkrockstudios.apps.hammer.common.preview.PreviewScrim
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectData
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectSyncOffContent

@Preview(widthDp = 600, heightDp = 400)
@Composable
fun ProjectSyncOffDialogPreview() {
	PreviewScrim {
		ProjectSyncOffContent(
			projectName = fakeProjectData().definition.name,
			onConfirm = {},
			onCancel = {},
		)
	}
}
