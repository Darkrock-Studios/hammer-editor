package com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.SceneEditor

@Composable
expect fun EditorTopBar(component: SceneEditor, snackbarHostState: SnackbarHostState)
