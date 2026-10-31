package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/** Share of the page width a mouse drag must cover to turn the page without a fling. */
private const val PAGE_TURN_FRACTION = 0.2f
private val PageTurnVelocity = 400.dp

/**
 * Lets a mouse drag page through [pagerState], which the pager's own gestures ignore. Touch and
 * other pointers are left to the pager. A press that stays within the touch slop is left alone,
 * so a click on a page still reaches its `clickable`; past it, the drag consumes the moves, which
 * cancels that click.
 *
 * A pointer change a child has already consumed (a zoomed image panning, say) cancels the gesture.
 */
@Composable
fun Modifier.mouseDragPaging(pagerState: PagerState): Modifier {
	// Pager offsets grow towards the next page, which lies to the left of the pointer in LTR.
	val towardsNext = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
	return pointerInput(pagerState, towardsNext) {
		coroutineScope {
			var settle: Job? = null
			awaitEachGesture {
				val down = awaitFirstDown(requireUnconsumed = false)
				if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) {
					return@awaitEachGesture
				}

				val velocityTracker = VelocityTracker()
				velocityTracker.addPointerInputChange(down)
				val slop = viewConfiguration.touchSlop
				var dragged = 0f
				while (abs(dragged) <= slop) {
					val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
					if (change == null || !change.pressed || change.isConsumed) return@awaitEachGesture
					velocityTracker.addPointerInputChange(change)
					dragged += change.positionChange().x
					if (abs(dragged) > slop) change.consume()
				}

				settle?.cancel()
				val startPage = (pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt()
				pagerState.dispatchRawDelta(towardsNext * (dragged - sign(dragged) * slop))
				val completed = horizontalDrag(down.id) { change ->
					val dx = change.positionChange().x
					dragged += dx
					velocityTracker.addPointerInputChange(change)
					pagerState.dispatchRawDelta(towardsNext * dx)
					change.consume()
				}

				val velocity = if (completed) towardsNext * velocityTracker.calculateVelocity().x else 0f
				val distance = towardsNext * dragged
				val direction = when {
					abs(velocity) > PageTurnVelocity.toPx() -> sign(velocity)
					abs(distance) > size.width * PAGE_TURN_FRACTION -> sign(distance)
					else -> 0f
				}
				val nearest = (pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt()
				val target = when {
					direction > 0f -> max(nearest, startPage + 1)
					direction < 0f -> min(nearest, startPage - 1)
					else -> nearest
				}.coerceIn(0, pagerState.pageCount - 1)
				settle = launch { pagerState.animateScrollToPage(target) }
			}
		}
	}
}
