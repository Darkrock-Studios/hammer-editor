package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.rememberDialogState
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.jthemedetecor.OsThemeDetector

@Composable
actual fun MpDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	size: DpSize?,
	content: @Composable () -> Unit
) {
	val osThemeDetector = remember { OsThemeDetector.getDetector() }
	val defaultSize = DpSize(400.dp, 300.dp)

	Dialog(
		onCloseRequest = onCloseRequest,
		visible = visible,
		title = title,
		state = rememberDialogState(size = size ?: defaultSize)
	) {
		var darkMode by remember { mutableStateOf(osThemeDetector.isDark) }
		osThemeDetector.registerListener { isDarkModeEnabled ->
			darkMode = isDarkModeEnabled
		}

		AppTheme(useDarkTheme = darkMode) {
			Surface(modifier = Modifier.fillMaxSize()) {
				content()
			}
		}
	}
}