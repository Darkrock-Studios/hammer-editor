package com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor

import androidx.compose.runtime.Composable
import dev.icerock.moko.resources.ImageResource

@Composable
internal expect fun EditorAction(
	iconRes: ImageResource,
	active: Boolean,
	onClick: () -> Unit,
)