package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog

@Composable
actual fun MpDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	resizable: Boolean,
	content: @Composable () -> Unit
) {
	Dialog(
		visible = visible,
		onCloseRequest = onCloseRequest,
		title = title,
		resizable = resizable
	) {
		content()
	}
}