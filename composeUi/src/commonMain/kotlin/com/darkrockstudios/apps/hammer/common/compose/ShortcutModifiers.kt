package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*

internal fun Modifier.onKeyShortcut(
	key: Key,
	ctrl: Boolean = false,
	shift: Boolean = false,
	action: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
	val ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed
	if (event.type == KeyEventType.KeyDown &&
		event.key == key &&
		ctrlOrMeta == ctrl &&
		event.isShiftPressed == shift
	) {
		action()
		true
	} else {
		false
	}
}

fun Modifier.findShortcutModifier(showFindBar: () -> Unit): Modifier =
	onKeyShortcut(Key.F, ctrl = true, action = showFindBar)

fun Modifier.saveShortcutModifier(onSave: () -> Unit): Modifier =
	onKeyShortcut(Key.S, ctrl = true, action = onSave)

fun Modifier.syncShortcutModifier(onSync: () -> Unit): Modifier =
	onKeyShortcut(Key.F3, action = onSync)

fun Modifier.boldShortcutModifier(onBold: () -> Unit): Modifier =
	onKeyShortcut(Key.B, ctrl = true, action = onBold)

fun Modifier.italicShortcutModifier(onItalic: () -> Unit): Modifier =
	onKeyShortcut(Key.I, ctrl = true, action = onItalic)

fun Modifier.strikethroughShortcutModifier(onStrikethrough: () -> Unit): Modifier =
	onKeyShortcut(Key.X, ctrl = true, shift = true, action = onStrikethrough)
