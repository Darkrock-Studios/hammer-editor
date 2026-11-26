package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.ChordPlatform
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import com.darkrockstudios.apps.hammer.common.compose.actions.toKeyChord
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** A window's Quit, registered at Root and reached by dispatching its previewed key events. */
@OptIn(InternalComposeUiApi::class)
class QuitChordTest {

	private var quits = 0
	private val actions = ActionRegistry().apply {
		register(ActionLayer.Root, mapOf(HammerActions.AppQuit.id to ActionHandler { quits++ }))
	}

	private fun press(
		key: Key,
		platform: ChordPlatform,
		ctrl: Boolean = false,
		meta: Boolean = false,
		alt: Boolean = false,
		shift: Boolean = false,
	) {
		val event = KeyEvent(
			key = key,
			type = KeyEventType.KeyDown,
			isCtrlPressed = ctrl,
			isMetaPressed = meta,
			isAltPressed = alt,
			isShiftPressed = shift,
		)
		event.toKeyChord(platform)?.let(actions::dispatch)
	}

	@Test
	fun `ctrl Q quits`() {
		press(Key.Q, ChordPlatform.Desktop, ctrl = true)
		assertEquals(1, quits)
	}

	@Test
	fun `cmd Q quits on macOS`() {
		press(Key.Q, ChordPlatform.DesktopMac, meta = true)
		assertEquals(1, quits)
	}

	@Test
	fun `ctrl Q does not quit on macOS`() {
		press(Key.Q, ChordPlatform.DesktopMac, ctrl = true)
		assertEquals(0, quits)
	}

	@Test
	fun `AltGr Q types an at sign instead of quitting`() {
		press(Key.Q, ChordPlatform.Desktop, ctrl = true, alt = true)
		assertEquals(0, quits)
	}

	@Test
	fun `ctrl shift Q does not quit`() {
		press(Key.Q, ChordPlatform.Desktop, ctrl = true, shift = true)
		assertEquals(0, quits)
	}

	@Test
	fun `ctrl Q quits with a menu open`() {
		actions.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true, onDismiss = {})
		press(Key.Q, ChordPlatform.Desktop, ctrl = true)
		assertEquals(1, quits)
	}
}
