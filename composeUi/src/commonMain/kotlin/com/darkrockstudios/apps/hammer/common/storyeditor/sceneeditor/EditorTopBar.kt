package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState

@Composable
expect fun EditorTopBar(
	component: SceneEditor,
	rootSnackbar: RootSnackbarHostState,
)
