package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.ui.input.key.*
import com.darkrockstudios.apps.hammer.common.compose.matchesShortcut

/**
 * Window level shortcuts, matched before the focused component sees the event.
 *
 * Modifiers must match exactly: AltGr arrives as Ctrl+Alt, so a loose `Ctrl+Q` test would
 * fire while the user types `@` on a Turkish or German keyboard.
 */
internal enum class WindowShortcut {
	Back,
	Quit,
	CloseProject,
	GlobalSearch,
}

internal fun KeyEvent.toProjectSelectionShortcut(): WindowShortcut? = when {
	isBack() -> WindowShortcut.Back
	matchesShortcut(Key.Q, ctrl = true) -> WindowShortcut.Quit
	else -> null
}

internal fun KeyEvent.toProjectEditorShortcut(): WindowShortcut? = when {
	isBack() -> WindowShortcut.Back
	matchesShortcut(Key.F, ctrl = true, shift = true) -> WindowShortcut.GlobalSearch
	matchesShortcut(Key.W, ctrl = true) -> WindowShortcut.CloseProject
	matchesShortcut(Key.Q, ctrl = true) -> WindowShortcut.Quit
	else -> null
}

private fun KeyEvent.isBack(): Boolean = key == Key.Escape && type == KeyEventType.KeyUp
