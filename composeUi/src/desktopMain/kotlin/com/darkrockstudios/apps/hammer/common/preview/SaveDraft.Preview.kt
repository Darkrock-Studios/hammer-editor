package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SaveDraftCard

/** Starting a new draft from the scene editor, over a scrim. */
@Composable
internal fun SaveDraftScrimPreview() {
	PreviewScrim {
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
