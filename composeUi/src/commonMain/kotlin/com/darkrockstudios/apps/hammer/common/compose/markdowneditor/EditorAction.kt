package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal expect fun EditorAction(
	icon: ImageVector,
	active: Boolean,
	onClick: () -> Unit,
)

@Composable
internal expect fun EditorTextAction(
	label: String,
	active: Boolean,
	onClick: () -> Unit,
)
