package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.ui.input.key.Key
import com.darkrockstudios.apps.hammer.common.data.KeyShortcut
import java.awt.event.KeyEvent

fun KeyShortcut.toDesktopShortcut(): androidx.compose.ui.input.key.KeyShortcut {
	return androidx.compose.ui.input.key.KeyShortcut(
		key = makeKey(keyCode),
		ctrl = ctrl,
		alt = alt,
		meta = meta,
		shift = shift
	)
}

/**
 * Creates instance of [Key].
 *
 * @param nativeKeyCode represents this key as defined in [java.awt.event.KeyEvent]
 * @param nativeKeyLocation represents the location of key as defined in [java.awt.event.KeyEvent]
 */
private fun makeKey(nativeKeyCode: Int, nativeKeyLocation: Int = KeyEvent.KEY_LOCATION_STANDARD): Key {
	// First 32 bits are for keycode.
	val keyCode = nativeKeyCode.toLong().shl(32)

	// Next 3 bits are for location.
	val location = (nativeKeyLocation.toLong() and 0x7).shl(29)

	return Key(keyCode or location)
}
