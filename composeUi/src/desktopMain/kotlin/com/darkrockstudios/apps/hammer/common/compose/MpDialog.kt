package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.jthemedetecor.OsThemeDetector

@Composable
actual fun MpDialog(
	onCloseRequest: () -> Unit,
	visible: Boolean,
	title: String,
	modifier: Modifier,
	content: @Composable () -> Unit
) {
	val osThemeDetector = remember { OsThemeDetector.getDetector() }

	// TODO This is crashing on desktop sometimes, look into it
	if (visible) {
		Dialog(
			onCloseRequest = onCloseRequest,
			visible = visible,
			title = title,
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
}