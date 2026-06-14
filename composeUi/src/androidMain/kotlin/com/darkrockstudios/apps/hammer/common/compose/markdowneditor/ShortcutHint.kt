package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

internal actual fun shortcutHint(key: String, shift: Boolean): String =
	"Ctrl+${if (shift) "Shift+" else ""}$key"
