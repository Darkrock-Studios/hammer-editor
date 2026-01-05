package com.darkrockstudios.apps.hammer.common.storyeditor

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*

fun Modifier.findShortcutModifier(showFindBar: () -> Unit): Modifier {
	return onPreviewKeyEvent { event ->
		// Handle Ctrl+F / Cmd+F to show find bar
		if (event.type == KeyEventType.KeyDown &&
			event.key == Key.F &&
			(event.isCtrlPressed || event.isMetaPressed)
		) {
			showFindBar()
			true
		} else {
			false
		}
	}
}