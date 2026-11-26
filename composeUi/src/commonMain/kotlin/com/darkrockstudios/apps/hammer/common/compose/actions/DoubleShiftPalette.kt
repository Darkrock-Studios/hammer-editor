package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.darkrockstudios.apps.hammer.common.components.actions.ActionDispatcher
import com.darkrockstudios.apps.hammer.common.components.actions.DoubleShiftDetector
import com.darkrockstudios.apps.hammer.common.components.actions.HammerActions
import kotlin.time.TimeSource

/** Opens the palette on a double tap of Shift. A window host feeds it every key event it previews. */
class DoubleShiftPalette(
	private val actions: ActionDispatcher,
	private val nowMs: () -> Long = monotonicMs(),
) {
	private val detector = DoubleShiftDetector()

	/** Observes only: the key event always goes on to whatever else handles it. */
	fun onPreviewKeyEvent(event: KeyEvent): Boolean {
		val isDown = when (event.type) {
			KeyEventType.KeyDown -> true
			KeyEventType.KeyUp -> false
			else -> return false
		}
		val isShift = event.key == Key.ShiftLeft || event.key == Key.ShiftRight
		if (detector.onKey(isShift, isDown, nowMs())) actions.invoke(HammerActions.PaletteOpen.id)
		return false
	}

	fun onPointerPress() = detector.reset()
}

/** Resets [palette] on any pointer press inside, so Shift+click twice never opens it. */
fun Modifier.resetsDoubleShift(palette: DoubleShiftPalette): Modifier =
	pointerInput(palette) {
		awaitPointerEventScope {
			while (true) {
				val event = awaitPointerEvent(PointerEventPass.Initial)
				if (event.type == PointerEventType.Press) palette.onPointerPress()
			}
		}
	}

private fun monotonicMs(): () -> Long {
	val start = TimeSource.Monotonic.markNow()
	return { start.elapsedNow().inWholeMilliseconds }
}
