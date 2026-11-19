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
import com.darkrockstudios.apps.hammer.common.projectselection.ProjectDeleteContent

/** Delete with the server option offered, over a scrim. */
@Preview(widthDp = 600, heightDp = 600)
@Composable
fun ProjectDeleteDialogPreview() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
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
