package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SaveDraftCard

/** Starting a new draft from the scene editor, over a scrim. */
@Composable
internal fun SaveDraftScrimPreview() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
		SaveDraftCard(
			currentDraftName = "First pass",
			currentDraftIsValid = true,
			draftName = "",
			onDraftNameChange = {},
			isValidDraftName = false,
			onConfirm = {},
			onCancel = {},
		)
	}
}
