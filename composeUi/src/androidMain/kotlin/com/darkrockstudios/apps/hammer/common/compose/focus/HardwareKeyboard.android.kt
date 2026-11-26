package com.darkrockstudios.apps.hammer.common.compose.focus

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
internal actual fun hasHardwareKeyboard(): Boolean {
	val configuration = LocalConfiguration.current
	return configuration.keyboard != Configuration.KEYBOARD_NOKEYS &&
		configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
}
