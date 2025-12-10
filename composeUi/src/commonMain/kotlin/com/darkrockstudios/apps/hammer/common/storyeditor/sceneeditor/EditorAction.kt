package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.DrawableResource

@Composable
internal expect fun EditorAction(
	iconRes: DrawableResource,
	active: Boolean,
	onClick: () -> Unit,
)