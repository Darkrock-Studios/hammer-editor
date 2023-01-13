package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun MpDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	modifier: Modifier,
	content: @Composable () -> Unit
) {
	if (visible) {
		AlertDialog(
			title = { Text(title) },
			onDismissRequest = { onCloseRequest() },
			buttons = {
				content()
			},
			modifier = modifier
		)
		/*
		Dialog(onDismissRequest = onCloseRequest) {
			content()
		}
		*/
	}
}