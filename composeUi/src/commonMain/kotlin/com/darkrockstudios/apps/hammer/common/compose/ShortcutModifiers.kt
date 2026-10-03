package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import com.darkrockstudios.texteditor.input.layoutKey

/**
 * Matches a key-down with exactly these modifiers: extra ones do not count as a match.
 * Letters match on [layoutKey], as the editor's built-in chords do.
 */
fun KeyEvent.matchesShortcut(
	key: Key,
	ctrl: Boolean = false,
	shift: Boolean = false,
	alt: Boolean = false,
): Boolean {
	val ctrlOrMeta = isCtrlPressed || isMetaPressed
	return type == KeyEventType.KeyDown &&
		layoutKey == key &&
		ctrlOrMeta == ctrl &&
		isShiftPressed == shift &&
		isAltPressed == alt
}

internal fun Modifier.onKeyShortcut(
	key: Key,
	ctrl: Boolean = false,
	shift: Boolean = false,
	alt: Boolean = false,
	action: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
	if (event.matchesShortcut(key, ctrl, shift, alt)) {
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

fun Modifier.boldShortcutModifier(onBold: () -> Unit): Modifier =
	onKeyShortcut(Key.B, ctrl = true, action = onBold)

fun Modifier.italicShortcutModifier(onItalic: () -> Unit): Modifier =
	onKeyShortcut(Key.I, ctrl = true, action = onItalic)

fun Modifier.strikethroughShortcutModifier(onStrikethrough: () -> Unit): Modifier =
	onKeyShortcut(Key.X, ctrl = true, shift = true, action = onStrikethrough)
