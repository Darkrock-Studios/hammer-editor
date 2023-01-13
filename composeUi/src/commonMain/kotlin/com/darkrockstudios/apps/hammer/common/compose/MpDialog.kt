package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MpDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit
)