package com.darkrockstudios.apps.hammer.common.preview.projectselection

import com.darkrockstudios.apps.hammer.common.preview.PreviewScrim
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectData
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectDeleteContent

/** Delete with the server option offered, over a scrim. */
@Preview(widthDp = 600, heightDp = 600)
@Composable
fun ProjectDeleteDialogPreview() {
	PreviewScrim {
		ProjectDeleteContent(
			projectName = fakeProjectData().definition.name,
			confirmationText = "",
			onConfirmationTextChange = {},
			mentionCoauthors = true,
			offerServerDelete = true,
			alsoDeleteOnServer = false,
			onAlsoDeleteOnServerChange = {},
			onConfirm = {},
			onCancel = {},
		)
	}
}
