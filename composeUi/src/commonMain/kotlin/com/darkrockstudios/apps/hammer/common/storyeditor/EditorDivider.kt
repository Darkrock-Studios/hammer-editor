package com.darkrockstudios.apps.hammer.common.storyeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

data class EditorDivider(
	val x: Dp = Dp.Unspecified,
)

@Composable
expect fun rememberEditorDivider(): EditorDivider