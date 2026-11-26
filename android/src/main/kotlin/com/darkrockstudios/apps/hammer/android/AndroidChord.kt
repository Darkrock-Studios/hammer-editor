package com.darkrockstudios.apps.hammer.android

import android.view.KeyEvent
import com.darkrockstudios.apps.hammer.common.components.actions.ActionDispatcher
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.DoubleShiftDetector
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord

/**
 * The chord a key press makes, or null when it is no chord: any Alt (AltGr arrives as
 * Ctrl+Alt), Meta, or a key outside [ChordKey]. Lock keys are ignored.
 */
fun androidChord(keyCode: Int, metaState: Int): KeyChord? {
	if ((metaState and (KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK)) != 0) return null
	val chordKey = chordKeys[keyCode] ?: return null
	return KeyChord(
		chordKey,
		primary = (metaState and KeyEvent.META_CTRL_MASK) != 0,
		shift = (metaState and KeyEvent.META_SHIFT_MASK) != 0,
	)
}

/** True when [actions] answered the key press; false means the host passes the event on. */
fun dispatchProjectKey(actions: ActionDispatcher?, action: Int, keyCode: Int, metaState: Int): Boolean {
	if (actions == null || action != KeyEvent.ACTION_DOWN) return false
	val chord = androidChord(keyCode, metaState) ?: return false
	return actions.dispatch(chord)
}

/** Feeds [detector] one key event and opens the palette on a double Shift. Never consumes it. */
fun feedDoubleShift(
	detector: DoubleShiftDetector,
	actions: ActionDispatcher?,
	action: Int,
	keyCode: Int,
	eventTimeMs: Long,
) {
	val isDown = when (action) {
		KeyEvent.ACTION_DOWN -> true
		KeyEvent.ACTION_UP -> false
		else -> return
	}
	val isShift = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
	if (detector.onKey(isShift, isDown, eventTimeMs)) actions?.invoke(HammerActions.PaletteOpen.id)
}

private fun ChordKey.through(last: ChordKey): List<ChordKey> = ChordKey.entries.subList(ordinal, last.ordinal + 1)

private val chordKeys: Map<Int, ChordKey> = buildMap {
	putAll((KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).zip(ChordKey.A.through(ChordKey.Z)))
	putAll((KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9).zip(ChordKey.Digit0.through(ChordKey.Digit9)))
	putAll((KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9).zip(ChordKey.Digit0.through(ChordKey.Digit9)))
	putAll((KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12).zip(ChordKey.F1.through(ChordKey.F12)))
	put(KeyEvent.KEYCODE_PAGE_UP, ChordKey.PageUp)
	put(KeyEvent.KEYCODE_PAGE_DOWN, ChordKey.PageDown)
	put(KeyEvent.KEYCODE_ENTER, ChordKey.Enter)
	put(KeyEvent.KEYCODE_NUMPAD_ENTER, ChordKey.Enter)
	put(KeyEvent.KEYCODE_EQUALS, ChordKey.Equals)
	put(KeyEvent.KEYCODE_MINUS, ChordKey.Minus)
}
