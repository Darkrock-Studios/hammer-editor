package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.ui.input.key.*
import com.darkrockstudios.apps.hammer.common.compose.matchesShortcut

/**
 * Modifiers must match exactly, and no chord may use Alt: AltGr arrives as Ctrl+Alt, so
 * either would fire while a Turkish, German, or Polish layout types the character on that
 * key.
 */
internal enum class WindowShortcut {
	Back,
	Quit,
	CloseProject,
	GlobalSearch,
}

/** Project actions, matched pre-focus so a focused editor cannot swallow them. */
internal enum class ProjectShortcut {
	SyncProject,
	SaveAll,
}

internal fun KeyEvent.toProjectShortcut(): ProjectShortcut? = when {
	matchesShortcut(Key.F3) -> ProjectShortcut.SyncProject
	matchesShortcut(Key.S, ctrl = true, shift = true) -> ProjectShortcut.SaveAll
	else -> null
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
