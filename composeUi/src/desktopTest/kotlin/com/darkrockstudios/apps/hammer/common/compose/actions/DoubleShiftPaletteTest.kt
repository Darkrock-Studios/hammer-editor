package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.components.actions.ActionHandler
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(InternalComposeUiApi::class, ExperimentalTestApi::class)
class DoubleShiftPaletteTest {

	private val registry = ActionRegistry()
	private var opened = 0
	private var now = 0L
	private val palette = DoubleShiftPalette(registry) { now }

	init {
		registry.register(ActionLayer.Root, mapOf(HammerActions.PaletteOpen.id to ActionHandler { opened++ }))
	}

	private fun key(key: Key, type: KeyEventType, at: Long): Boolean {
		now = at
		return palette.onPreviewKeyEvent(KeyEvent(key = key, type = type))
	}

	private fun tap(key: Key, at: Long) {
		key(key, KeyEventType.KeyDown, at)
		key(key, KeyEventType.KeyUp, at + 80)
	}

	@Test
	fun `left then right Shift opens the palette`() {
		tap(Key.ShiftLeft, 0)
		tap(Key.ShiftRight, 200)

		assertEquals(1, opened)
	}

	@Test
	fun `the Shift events still go on to the content`() {
		val consumed = listOf(
			key(Key.ShiftLeft, KeyEventType.KeyDown, 0),
			key(Key.ShiftLeft, KeyEventType.KeyUp, 80),
			key(Key.ShiftLeft, KeyEventType.KeyDown, 200),
			key(Key.ShiftLeft, KeyEventType.KeyUp, 280),
		)

		assertFalse(consumed.any { it })
	}

	@Test
	fun `Shift around a letter does not open it`() {
		key(Key.ShiftLeft, KeyEventType.KeyDown, 0)
		key(Key.A, KeyEventType.KeyDown, 30)
		key(Key.A, KeyEventType.KeyUp, 60)
		key(Key.ShiftLeft, KeyEventType.KeyUp, 80)
		tap(Key.ShiftLeft, 200)

		assertEquals(0, opened)
	}

	@Test
	fun `a click between the taps does not open it`() = runComposeUiTest {
		setContent { Box(Modifier.size(100.dp).testTag("content").resetsDoubleShift(palette)) }
		tap(Key.ShiftLeft, 0)

		onNodeWithTag("content").performClick()
		waitForIdle()
		tap(Key.ShiftLeft, 200)

		assertEquals(0, opened)
	}
}
