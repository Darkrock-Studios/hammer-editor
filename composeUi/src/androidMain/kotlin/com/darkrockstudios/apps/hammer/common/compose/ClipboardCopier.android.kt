package com.darkrockstudios.apps.hammer.common.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberClipboardCopier(): (String) -> Unit {
	val context = LocalContext.current
	return { text ->
		val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
	}
}
