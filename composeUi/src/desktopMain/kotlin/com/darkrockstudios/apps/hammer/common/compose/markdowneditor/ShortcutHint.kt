package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.hostOs

internal actual fun shortcutHint(key: String, shift: Boolean): String =
	if (hostOs == HostOs.MacOs) {
		"⌘${if (shift) "⇧" else ""}$key"
	} else {
		"Ctrl+${if (shift) "Shift+" else ""}$key"
	}
