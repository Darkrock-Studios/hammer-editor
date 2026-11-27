package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.ChordPlatform
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.components.actions.currentChordPlatform
import com.darkrockstudios.texteditor.input.layoutKey

/**
 * The chord a key press makes, or null when it is no chord: a release, any Alt (AltGr
 * arrives as Ctrl+Alt), the non-Mod one of Ctrl and Meta, or a key outside [ChordKey].
 * Letters follow the active layout as the editor's own chords do. Digit symbols are never
 * aliased: X11 reports the same key for AZERTY's 4 and QWERTY's apostrophe.
 */
fun KeyEvent.toKeyChord(platform: ChordPlatform = currentChordPlatform): KeyChord? {
	if (type != KeyEventType.KeyDown) return null
	if (isAltPressed) return null
	val primary = if (platform.modIsCmd) isMetaPressed else isCtrlPressed
	val other = if (platform.modIsCmd) isCtrlPressed else isMetaPressed
	if (other) return null
	val chordKey = letterKeys[layoutKey] ?: otherKeys[key] ?: return null
	return KeyChord(chordKey, primary = primary, shift = isShiftPressed)
}

private val letterKeys: Map<Key, ChordKey> = mapOf(
	Key.A to ChordKey.A, Key.B to ChordKey.B, Key.C to ChordKey.C, Key.D to ChordKey.D,
	Key.E to ChordKey.E, Key.F to ChordKey.F, Key.G to ChordKey.G, Key.H to ChordKey.H,
	Key.I to ChordKey.I, Key.J to ChordKey.J, Key.K to ChordKey.K, Key.L to ChordKey.L,
	Key.M to ChordKey.M, Key.N to ChordKey.N, Key.O to ChordKey.O, Key.P to ChordKey.P,
	Key.Q to ChordKey.Q, Key.R to ChordKey.R, Key.S to ChordKey.S, Key.T to ChordKey.T,
	Key.U to ChordKey.U, Key.V to ChordKey.V, Key.W to ChordKey.W, Key.X to ChordKey.X,
	Key.Y to ChordKey.Y, Key.Z to ChordKey.Z,
)

private val otherKeys: Map<Key, ChordKey> = mapOf(
	Key.Zero to ChordKey.Digit0, Key.One to ChordKey.Digit1, Key.Two to ChordKey.Digit2,
	Key.Three to ChordKey.Digit3, Key.Four to ChordKey.Digit4, Key.Five to ChordKey.Digit5,
	Key.Six to ChordKey.Digit6, Key.Seven to ChordKey.Digit7, Key.Eight to ChordKey.Digit8,
	Key.Nine to ChordKey.Digit9,
	Key.NumPad0 to ChordKey.Digit0, Key.NumPad1 to ChordKey.Digit1, Key.NumPad2 to ChordKey.Digit2,
	Key.NumPad3 to ChordKey.Digit3, Key.NumPad4 to ChordKey.Digit4, Key.NumPad5 to ChordKey.Digit5,
	Key.NumPad6 to ChordKey.Digit6, Key.NumPad7 to ChordKey.Digit7, Key.NumPad8 to ChordKey.Digit8,
	Key.NumPad9 to ChordKey.Digit9,
	Key.F1 to ChordKey.F1, Key.F2 to ChordKey.F2, Key.F3 to ChordKey.F3, Key.F4 to ChordKey.F4,
	Key.F5 to ChordKey.F5, Key.F6 to ChordKey.F6, Key.F7 to ChordKey.F7, Key.F8 to ChordKey.F8,
	Key.F9 to ChordKey.F9, Key.F10 to ChordKey.F10, Key.F11 to ChordKey.F11, Key.F12 to ChordKey.F12,
	Key.PageUp to ChordKey.PageUp, Key.PageDown to ChordKey.PageDown,
	Key.NumPadPageUp to ChordKey.PageUp, Key.NumPadPageDown to ChordKey.PageDown,
	Key.Enter to ChordKey.Enter, Key.NumPadEnter to ChordKey.Enter,
	Key.Equals to ChordKey.Equals, Key.Minus to ChordKey.Minus,
)
