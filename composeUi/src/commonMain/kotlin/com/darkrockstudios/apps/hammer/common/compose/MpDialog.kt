package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize

@Composable
expect fun MpDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	size: DpSize? = null,
	content: @Composable () -> Unit
)