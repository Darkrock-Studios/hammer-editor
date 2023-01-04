package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable

@Composable
expect fun MpDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean = true,
	title: String = "Untitled",
	resizable: Boolean = true,
	content: @Composable () -> Unit
)