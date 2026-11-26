package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Lets a mouse reach a horizontal row's items past the edge: the vertical wheel scrolls it sideways,
 * and a drag pans it. Touch and horizontal wheels already scroll it.
 */
internal fun Modifier.mouseScrollsSideways(state: ScrollableState): Modifier = pointerInput(state) {
	val wheelStep = WHEEL_STEP.toPx()
	coroutineScope {
		launch {
			awaitPointerEventScope {
				while (true) {
					val event = awaitPointerEvent()
					if (event.type != PointerEventType.Scroll || event.keyboardModifiers.isShiftPressed) continue
					val change = event.changes.firstOrNull() ?: continue
					val delta = change.scrollDelta
					if (delta.y == 0f || abs(delta.x) >= abs(delta.y)) continue
					if (state.dispatchRawDelta(delta.y * wheelStep) != 0f) change.consume()
				}
			}
		}
		launch {
			awaitEachGesture {
				val down = awaitFirstDown(requireUnconsumed = false)
				if (down.type != PointerType.Mouse) return@awaitEachGesture
				val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
					?: return@awaitEachGesture
				state.dispatchRawDelta(down.position.x - start.position.x)
				horizontalDrag(start.id) { change ->
					state.dispatchRawDelta(-change.positionChange().x)
					change.consume()
				}
			}
		}
	}
}

private val WHEEL_STEP = 48.dp
