package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog

@Composable
actual fun MpDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	modifier: Modifier,
	content: @Composable () -> Unit
) {
	// TODO This is crashing on desktop sometimes, look into it
	if (visible) {
		Dialog(
			onCloseRequest = onCloseRequest,
			visible = visible,
			title = title,
		) {
			content()
		}
	}
}