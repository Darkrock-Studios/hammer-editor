package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.storyeditor.SceneKind
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneRenameCard

/** Renaming a scene from the phone editor's top bar, over a scrim. */
@Composable
internal fun EditorRenameSceneScrimPreview() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
		contentAlignment = Alignment.Center,
	) {
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
