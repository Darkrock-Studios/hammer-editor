package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.darkrockstudios.apps.hammer.common.components.actions.ActionLayer
import com.darkrockstudios.apps.hammer.common.components.actions.ActionRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class EscapeRouterTest {

	private val registry = ActionRegistry()
	private var backs = 0
	private var dismissals = 0
	private val router = EscapeRouter(registry) {
		backs++
		true
	}

	private val press = KeyEvent(key = Key.Escape, type = KeyEventType.KeyDown)
	private val release = KeyEvent(key = Key.Escape, type = KeyEventType.KeyUp)

	/** A window delivering [event]: preview first, then content, then the window's onKeyEvent. */
	private fun deliver(event: KeyEvent, contentTakesIt: Boolean = false): Boolean {
		if (router.onPreviewKeyEvent(event)) return true
		if (contentTakesIt) return true
		return router.onKeyEvent(event)
	}

	private fun openOverlay() =
		registry.register(ActionLayer.Overlay, emptyMap(), blocksBelow = true, onDismiss = { dismissals++ })

	@Test
	fun `Esc with an overlay up dismisses it and never steps back`() {
		openOverlay()

		val pressConsumed = deliver(press)
		val releaseConsumed = deliver(release)

		assertTrue(pressConsumed)
		assertTrue(releaseConsumed)
		assertEquals(1, dismissals)
		assertEquals(0, backs)
	}

	@Test
	fun `Esc with no overlay steps back once`() {
		val pressConsumed = deliver(press)
		deliver(release)

		assertFalse(pressConsumed)
		assertEquals(1, backs)
	}

	@Test
	fun `an Esc release without its press does nothing`() {
		val consumed = deliver(release)

		assertFalse(consumed)
		assertEquals(0, backs)
	}

	@Test
	fun `a press the dialog took itself does not step back on release`() {
		val overlay = openOverlay()

		deliver(press, contentTakesIt = true)
		overlay.unregister()
		deliver(release)

		assertEquals(0, backs)
		assertEquals(0, dismissals)
	}

	@Test
	fun `an overlay gone by the release still keeps its press from stepping back`() {
		val overlay = openOverlay()

		deliver(press)
		overlay.unregister()
		deliver(release)

		assertEquals(1, dismissals)
		assertEquals(0, backs)
	}

	@Test
	fun `an overlay opened before the release keeps it from stepping back`() {
		deliver(press)
		openOverlay()
		deliver(release)

		assertEquals(0, backs)
	}

	@Test
	fun `holding Esc dismisses one overlay`() {
		openOverlay()
		openOverlay()

		deliver(press)
		val repeatConsumed = deliver(press)
		deliver(press)
		deliver(release)

		assertTrue(repeatConsumed)
		assertEquals(1, dismissals)
		assertEquals(0, backs)
	}

	@Test
	fun `a release content took does not stale the next press`() {
		deliver(press)
		deliver(release, contentTakesIt = true)
		openOverlay()

		deliver(press)
		deliver(release)

		assertEquals(1, dismissals)
		assertEquals(0, backs)
	}

	@Test
	fun `other keys pass through`() {
		openOverlay()

		val consumed = deliver(KeyEvent(key = Key.A, type = KeyEventType.KeyDown))

		assertFalse(consumed)
		assertEquals(0, dismissals)
	}
}
