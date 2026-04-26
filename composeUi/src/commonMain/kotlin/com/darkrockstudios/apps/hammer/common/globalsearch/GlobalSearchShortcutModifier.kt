package com.darkrockstudios.apps.hammer.common.globalsearch

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*

fun Modifier.globalSearchShortcutModifier(showGlobalSearch: () -> Unit): Modifier {
	return onPreviewKeyEvent { event ->
		if (event.type == KeyEventType.KeyDown &&
			event.key == Key.F &&
			event.isShiftPressed &&
			(event.isCtrlPressed || event.isMetaPressed)
		) {
			showGlobalSearch()
			true
		} else {
			false
		}
	}
}
