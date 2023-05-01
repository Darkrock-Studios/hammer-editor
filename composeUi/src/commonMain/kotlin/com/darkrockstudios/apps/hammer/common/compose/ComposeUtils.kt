package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.moveFocusOnTab(
	focusManager: FocusManager = LocalFocusManager.current
) = onPreviewKeyEvent {
	if (it.type == KeyEventType.KeyDown && it.key == Key.Tab) {
		focusManager.moveFocus(
			if (it.isShiftPressed) FocusDirection.Previous
			else FocusDirection.Next
		)
		return@onPreviewKeyEvent true
	}
	false
}