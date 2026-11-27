package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordPlatform
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class WindowChordsTest {

	private val registry = ActionRegistry()
	private val chords = WindowChords(registry, ChordPlatform.Desktop)

	private fun down(key: Key, ctrl: Boolean = true) =
		chords.onPreviewKeyEvent(KeyEvent(key = key, type = KeyEventType.KeyDown, isCtrlPressed = ctrl))

	private fun up(key: Key, ctrl: Boolean = true) =
		chords.onPreviewKeyEvent(KeyEvent(key = key, type = KeyEventType.KeyUp, isCtrlPressed = ctrl))

	@Test
	fun `a held Ctrl+K opens the palette once`() {
		var toggles = 0
		registry.register(ActionLayer.Root, mapOf(HammerActions.PaletteOpen.id to ActionHandler { toggles++ }))

		down(Key.CtrlLeft, ctrl = false)
		repeat(5) { assertTrue(down(Key.K)) }
		up(Key.K)

		assertEquals(1, toggles)
	}

	@Test
	fun `tapping K twice under a held Ctrl toggles twice`() {
		var toggles = 0
		registry.register(ActionLayer.Root, mapOf(HammerActions.PaletteOpen.id to ActionHandler { toggles++ }))

		down(Key.CtrlLeft, ctrl = false)
		down(Key.K)
		up(Key.K)
		down(Key.K)
		up(Key.K)

		assertEquals(2, toggles)
	}

	@Test
	fun `a held Ctrl+PageDown steps on every repeat`() {
		var steps = 0
		registry.register(ActionLayer.Module, mapOf(HammerActions.ItemNext.id to ActionHandler { steps++ }))

		repeat(3) { down(Key.PageDown) }

		assertEquals(3, steps)
	}

	@Test
	fun `a release is never taken`() {
		registry.register(ActionLayer.Root, mapOf(HammerActions.PaletteOpen.id to ActionHandler {}))

		down(Key.K)

		assertFalse(up(Key.K))
	}
}
