package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.darkrockstudios.apps.hammer.common.HostOs
import com.darkrockstudios.apps.hammer.common.components.actions.ChordKey
import com.darkrockstudios.apps.hammer.common.components.actions.ChordPlatform
import com.darkrockstudios.apps.hammer.common.components.actions.KeyChord
import com.darkrockstudios.apps.hammer.common.hostOs
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.awt.Canvas
import java.awt.event.InputEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.KEY_LOCATION_STANDARD
import java.awt.event.KeyEvent.KEY_PRESSED
import java.awt.event.KeyEvent.VK_N
import java.awt.event.KeyEvent.VK_X
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(InternalComposeUiApi::class)
class KeyChordComposeTest {

	private fun keyDown(
		key: Key,
		ctrl: Boolean = false,
		meta: Boolean = false,
		alt: Boolean = false,
		shift: Boolean = false,
		type: KeyEventType = KeyEventType.KeyDown,
	) = KeyEvent(
		key = key,
		type = type,
		isCtrlPressed = ctrl,
		isMetaPressed = meta,
		isAltPressed = alt,
		isShiftPressed = shift,
	)

	/** An AWT key press as XToolkit reports it: [keyCode] from the first layout, [extended] from the active one. */
	private class X11KeyPress(keyCode: Int, private val extended: Int, modifiers: Int) :
		java.awt.event.KeyEvent(Canvas(), KEY_PRESSED, 0L, modifiers, keyCode, CHAR_UNDEFINED, KEY_LOCATION_STANDARD) {
		override fun getExtendedKeyCode(): Int = extended
	}

	@Test
	fun `ctrl N is Mod+N off Apple`() {
		assertEquals(KeyChord.mod(ChordKey.N), keyDown(Key.N, ctrl = true).toKeyChord(ChordPlatform.Desktop))
	}

	@Test
	fun `cmd N is Mod+N on Apple`() {
		assertEquals(KeyChord.mod(ChordKey.N), keyDown(Key.N, meta = true).toKeyChord(ChordPlatform.DesktopMac))
	}

	@Test
	fun `on Apple, Ctrl chords are not actions`() {
		assertNull(keyDown(Key.N, ctrl = true).toKeyChord(ChordPlatform.DesktopMac))
		assertNull(keyDown(Key.K, ctrl = true).toKeyChord(ChordPlatform.DesktopMac))
		assertNull(keyDown(Key.N, ctrl = true, meta = true).toKeyChord(ChordPlatform.DesktopMac))
	}

	@Test
	fun `off Apple, Meta chords are not actions`() {
		assertNull(keyDown(Key.N, meta = true).toKeyChord(ChordPlatform.Desktop))
		assertNull(keyDown(Key.N, ctrl = true, meta = true).toKeyChord(ChordPlatform.Desktop))
	}

	@Test
	fun `AltGr chords are never actions`() {
		assertNull(keyDown(Key.Q, ctrl = true, alt = true).toKeyChord(ChordPlatform.Desktop))
		assertNull(keyDown(Key.S, ctrl = true, alt = true, shift = true).toKeyChord(ChordPlatform.Desktop))
		assertNull(keyDown(Key.N, meta = true, alt = true).toKeyChord(ChordPlatform.DesktopMac))
		assertNull(keyDown(Key.F6, alt = true).toKeyChord(ChordPlatform.Desktop))
	}

	@Test
	fun `a key release is not a chord`() {
		assertNull(keyDown(Key.N, ctrl = true, type = KeyEventType.KeyUp).toKeyChord(ChordPlatform.Desktop))
	}

	@Test
	fun `a letter chord follows the active layout`() {
		assumeTrue(hostOs == HostOs.Linux)
		val bepoN = KeyEvent(
			key = Key(VK_X),
			type = KeyEventType.KeyDown,
			isCtrlPressed = true,
			nativeEvent = X11KeyPress(VK_X, extended = VK_N, modifiers = InputEvent.CTRL_DOWN_MASK),
		)

		assertEquals(KeyChord.mod(ChordKey.N), bepoN.toKeyChord(ChordPlatform.Desktop))
	}

	@Test
	fun `numpad digits alias the digit row`() {
		assertEquals(KeyChord.mod(ChordKey.Digit3), keyDown(Key.NumPad3, ctrl = true).toKeyChord(ChordPlatform.Desktop))
		assertEquals(KeyChord.mod(ChordKey.Digit1), keyDown(Key.One, ctrl = true).toKeyChord(ChordPlatform.Desktop))
	}

	@Test
	fun `the apostrophe key is not a digit`() {
		assertNull(keyDown(Key.Apostrophe, ctrl = true).toKeyChord(ChordPlatform.Desktop))
	}

	@Test
	fun `numpad enter is enter`() {
		assertEquals(
			KeyChord.mod(ChordKey.Enter, shift = true),
			keyDown(Key.NumPadEnter, ctrl = true, shift = true).toKeyChord(ChordPlatform.Desktop),
		)
	}

	@Test
	fun `function keys need no modifier`() {
		assertEquals(KeyChord.bare(ChordKey.F6), keyDown(Key.F6).toKeyChord(ChordPlatform.Desktop))
		assertEquals(KeyChord.bare(ChordKey.F6, shift = true), keyDown(Key.F6, shift = true).toKeyChord(ChordPlatform.Desktop))
	}

	@Test
	fun `keys outside the table are not chords`() {
		assertNull(keyDown(Key.Tab, ctrl = true).toKeyChord(ChordPlatform.Desktop))
	}
}
