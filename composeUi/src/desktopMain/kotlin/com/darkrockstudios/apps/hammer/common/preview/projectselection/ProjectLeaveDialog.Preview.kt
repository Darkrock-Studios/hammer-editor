package com.darkrockstudios.apps.hammer.common.preview.projectselection

import com.darkrockstudios.apps.hammer.common.preview.PreviewScrim
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectData
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectLeaveContent

@Preview(widthDp = 600, heightDp = 400)
@Composable
fun ProjectLeaveDialogPreview() {
	PreviewScrim {
		ProjectLeaveContent(
			projectName = fakeProjectData().definition.name,
			ownerName = "Alice",
			onConfirm = {},
			onCancel = {},
		)
	}
}
