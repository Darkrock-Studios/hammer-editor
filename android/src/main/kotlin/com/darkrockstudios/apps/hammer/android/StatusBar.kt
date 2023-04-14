package com.darkrockstudios.apps.hammer.android

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

@Composable
fun SetStatusBar() {
	val activity = LocalView.current.context as Activity
	val backgroundArgb = if (isSystemInDarkTheme()) {
		MaterialTheme.colorScheme.surface.toArgb()
	} else {
		MaterialTheme.colorScheme.inverseSurface.toArgb()
	}

	activity.window?.statusBarColor = backgroundArgb
}