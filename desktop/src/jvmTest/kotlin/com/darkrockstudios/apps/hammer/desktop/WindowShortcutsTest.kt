package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.darkrockstudios.apps.hammer.common.components.actions.ChordPlatform
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(InternalComposeUiApi::class)
class WindowShortcutsTest {

	private fun keyDown(
		key: Key,
		ctrl: Boolean = false,
		meta: Boolean = false,
		alt: Boolean = false,
		shift: Boolean = false,
	) = KeyEvent(
		key = key,
		type = KeyEventType.KeyDown,
		isCtrlPressed = ctrl,
		isMetaPressed = meta,
		isAltPressed = alt,
		isShiftPressed = shift,
	)

	@Test
	fun `ctrl Q quits from the project selection window`() {
		assertEquals(
			WindowShortcut.Quit,
			keyDown(Key.Q, ctrl = true).toProjectSelectionShortcut(ChordPlatform.Desktop),
		)
	}

	@Test
	fun `cmd Q quits from the project selection window`() {
		assertEquals(
			WindowShortcut.Quit,
			keyDown(Key.Q, meta = true).toProjectSelectionShortcut(ChordPlatform.DesktopMac),
		)
	}

	@Test
	fun `ctrl Q does not quit on macOS`() {
		assertNull(keyDown(Key.Q, ctrl = true).toProjectSelectionShortcut(ChordPlatform.DesktopMac))
	}

	@Test
	fun `AltGr Q types an at sign instead of quitting`() {
		assertNull(keyDown(Key.Q, ctrl = true, alt = true).toProjectSelectionShortcut(ChordPlatform.Desktop))
	}

	@Test
	fun `escape navigates back on key up only`() {
		val keyUp = KeyEvent(key = Key.Escape, type = KeyEventType.KeyUp)

		assertEquals(WindowShortcut.Back, keyUp.toProjectSelectionShortcut(ChordPlatform.Desktop))
		assertNull(keyDown(Key.Escape).toProjectSelectionShortcut(ChordPlatform.Desktop))
	}

	@Test
	fun `unmodified keys are not shortcuts`() {
		assertNull(keyDown(Key.Q).toProjectSelectionShortcut(ChordPlatform.Desktop))
	}

	@Test
	fun `shift Q does not quit`() {
		assertNull(keyDown(Key.Q, ctrl = true, shift = true).toProjectSelectionShortcut(ChordPlatform.Desktop))
	}
}
