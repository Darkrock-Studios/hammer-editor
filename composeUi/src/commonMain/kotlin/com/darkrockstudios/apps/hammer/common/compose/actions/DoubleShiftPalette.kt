package com.darkrockstudios.apps.hammer.common.compose.actions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.darkrockstudios.apps.hammer.common.components.actions.ActionDispatcher
import com.darkrockstudios.apps.hammer.common.components.actions.DoubleShiftDetector
import com.darkrockstudios.apps.hammer.common.compose.focus.onAnyPointerPress
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
		detector.feed(isShift, isDown, nowMs(), actions)
		return false
	}

	fun onPointerPress() = detector.reset()
}

/** The window's [DoubleShiftPalette], for content in a dialog layer the window's root never sees. */
val LocalDoubleShiftPalette = staticCompositionLocalOf<DoubleShiftPalette?> { null }

/**
 * Resets the window's [LocalDoubleShiftPalette] on any pointer press inside, so Shift+click twice
 * never opens it. Dialogs are their own layer, so their shells apply this as well as the window.
 */
@Composable
fun Modifier.resetsDoubleShift(): Modifier {
	val palette = LocalDoubleShiftPalette.current ?: return this
	return onAnyPointerPress(palette::onPointerPress)
}

private fun monotonicMs(): () -> Long {
	val start = TimeSource.Monotonic.markNow()
	return { start.elapsedNow().inWholeMilliseconds }
}
