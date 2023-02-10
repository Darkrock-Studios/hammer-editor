package com.darkrockstudios.apps.hammer.android

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

@Composable
fun SetStatusBar() {
	val activity = LocalView.current.context as Activity
	val backgroundArgb = MaterialTheme.colorScheme.background.toArgb()
	activity.window?.statusBarColor = backgroundArgb
}