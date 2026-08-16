package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
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
		assertEquals(WindowShortcut.Quit, keyDown(Key.Q, ctrl = true).toProjectSelectionShortcut())
	}

	@Test
	fun `cmd Q quits from the project selection window`() {
		assertEquals(WindowShortcut.Quit, keyDown(Key.Q, meta = true).toProjectSelectionShortcut())
	}

	@Test
	fun `AltGr Q types an at sign instead of quitting`() {
		val altGrQ = keyDown(Key.Q, ctrl = true, alt = true)

		assertNull(altGrQ.toProjectSelectionShortcut())
		assertNull(altGrQ.toProjectEditorShortcut())
	}

	@Test
	fun `AltGr W does not close the project`() {
		assertNull(keyDown(Key.W, ctrl = true, alt = true).toProjectEditorShortcut())
	}

	@Test
	fun `ctrl W closes the project`() {
		assertEquals(WindowShortcut.CloseProject, keyDown(Key.W, ctrl = true).toProjectEditorShortcut())
	}

	@Test
	fun `ctrl shift F opens global search`() {
		assertEquals(
			WindowShortcut.GlobalSearch,
			keyDown(Key.F, ctrl = true, shift = true).toProjectEditorShortcut(),
		)
	}

	@Test
	fun `escape navigates back on key up only`() {
		val keyUp = KeyEvent(key = Key.Escape, type = KeyEventType.KeyUp)

		assertEquals(WindowShortcut.Back, keyUp.toProjectSelectionShortcut())
		assertEquals(WindowShortcut.Back, keyUp.toProjectEditorShortcut())
		assertNull(keyDown(Key.Escape).toProjectEditorShortcut())
	}

	@Test
	fun `unmodified keys are not shortcuts`() {
		assertNull(keyDown(Key.Q).toProjectSelectionShortcut())
		assertNull(keyDown(Key.W).toProjectEditorShortcut())
	}

	@Test
	fun `F3 starts a project sync`() {
		assertEquals(ProjectShortcut.SyncProject, keyDown(Key.F3).toProjectShortcut())
		assertNull(keyDown(Key.F3, ctrl = true).toProjectShortcut())
	}

	@Test
	fun `ctrl shift S saves all buffers`() {
		assertEquals(
			ProjectShortcut.SaveAll,
			keyDown(Key.S, ctrl = true, shift = true).toProjectShortcut(),
		)
	}

	@Test
	fun `AltGr S types its character instead of saving`() {
		assertNull(keyDown(Key.S, ctrl = true, alt = true).toProjectShortcut())
		assertNull(
			keyDown(Key.S, ctrl = true, alt = true, shift = true).toProjectShortcut(),
		)
	}
}
