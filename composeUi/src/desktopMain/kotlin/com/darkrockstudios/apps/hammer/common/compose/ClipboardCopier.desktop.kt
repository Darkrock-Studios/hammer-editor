package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
actual fun rememberClipboardCopier(): (String) -> Unit = { text ->
	Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}
