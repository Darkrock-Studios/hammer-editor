package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

/** Renders a Ctrl/Cmd[+Shift]+key shortcut for display, using ⌘/⇧ on Apple platforms. */
internal expect fun shortcutHint(key: String, shift: Boolean = false): String
