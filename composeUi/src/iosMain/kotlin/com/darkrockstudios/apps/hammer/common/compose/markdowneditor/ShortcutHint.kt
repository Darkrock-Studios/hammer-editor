package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

internal actual fun shortcutHint(key: String, shift: Boolean): String =
	"⌘${if (shift) "⇧" else ""}$key"
