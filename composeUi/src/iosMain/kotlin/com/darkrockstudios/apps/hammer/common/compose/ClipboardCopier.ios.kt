package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import platform.UIKit.UIPasteboard

@Composable
actual fun rememberClipboardCopier(): (String) -> Unit = { text ->
	UIPasteboard.generalPasteboard.string = text
}
