package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.darkrockstudios.apps.hammer.common.components.actions.ChordPlatform
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
		assertTrue(keyDown(Key.Q, ctrl = true).isProjectSelectionQuit(ChordPlatform.Desktop))
	}

	@Test
	fun `cmd Q quits from the project selection window`() {
		assertTrue(keyDown(Key.Q, meta = true).isProjectSelectionQuit(ChordPlatform.DesktopMac))
	}

	@Test
	fun `ctrl Q does not quit on macOS`() {
		assertFalse(keyDown(Key.Q, ctrl = true).isProjectSelectionQuit(ChordPlatform.DesktopMac))
	}

	@Test
	fun `AltGr Q types an at sign instead of quitting`() {
		assertFalse(keyDown(Key.Q, ctrl = true, alt = true).isProjectSelectionQuit(ChordPlatform.Desktop))
	}

	@Test
	fun `unmodified keys are not shortcuts`() {
		assertFalse(keyDown(Key.Q).isProjectSelectionQuit(ChordPlatform.Desktop))
	}

	@Test
	fun `shift Q does not quit`() {
		assertFalse(keyDown(Key.Q, ctrl = true, shift = true).isProjectSelectionQuit(ChordPlatform.Desktop))
	}
}
