package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal expect fun EditorAction(
	icon: ImageVector,
	active: Boolean,
	onClick: () -> Unit,
)
