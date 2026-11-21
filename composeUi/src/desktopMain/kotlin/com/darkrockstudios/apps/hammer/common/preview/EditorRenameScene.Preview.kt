package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.storyeditor.SceneKind
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneRenameCard

/** Renaming a scene from the phone editor's top bar, over a scrim. */
@Composable
internal fun EditorRenameSceneScrimPreview() {
	PreviewScrim {
		SceneRenameCard(
			kind = SceneKind.Scene,
			name = "The Lamp Room",
			onNameChange = {},
			error = null,
			confirmEnabled = true,
			onConfirm = {},
			onCancel = {},
		)
	}
}
