package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog

@Composable
actual fun MpDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	content: @Composable () -> Unit
) {
	Dialog(visible = visible, onCloseRequest = onCloseRequest) {
		content()
	}
}