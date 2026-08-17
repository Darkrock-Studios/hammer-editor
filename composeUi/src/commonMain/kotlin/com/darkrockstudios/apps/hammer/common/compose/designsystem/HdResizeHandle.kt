package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

const val HD_RESIZE_HANDLE_TAG = "hd-resize-handle"

val RESIZE_HANDLE_WIDTH = 16.dp

/** Minimum horizontal slack beyond the column's minimum width before a resize handle is worth showing. */
val RESIZE_HANDLE_MIN_SLACK = 48.dp

private val RESIZE_HANDLE_TOUCH_WIDTH = 48.dp
private const val RESIZE_HANDLE_REST_ALPHA = 0.3f

/**
 * Drag state for a centered column resized by [HdResizeHandle]. Holds the in-flight
 * width locally during a drag and fires [onCommit] once on drag end, so callers can
 * persist without per-frame writes.
 */
@Stable
class HdResizeHandleState internal constructor() {
	internal var clampWidth: (Float) -> Float = { it }
	internal var onCommit: (Float) -> Unit = {}
	internal var onReset: () -> Unit = {}
	internal var persistedWidth by mutableStateOf(0f)
	internal var availableWidth by mutableStateOf(0.dp)

	private var dragWidth by mutableStateOf<Float?>(null)
	private var dragging = false

	/** Live column max width in dp: the in-flight drag value, else the persisted one. */
	val width: Dp get() = (dragWidth ?: persistedWidth).dp

	/**
	 * True when the window is wide enough for resizing to be meaningful. Keyed off the
	 * column's minimum width, never its current one, so the handle (and its reset) stays
	 * reachable even when the persisted width exceeds the window, and never un-composes
	 * mid-drag (which would drop the draggable's onDragStopped and lose the commit).
	 */
	val showHandle: Boolean get() = availableWidth >= clampWidth(0f).dp + RESIZE_HANDLE_MIN_SLACK

	fun onOutwardDrag(delta: Dp) {
		dragging = true
		val target = (dragWidth ?: persistedWidth) + 2 * delta.value
		dragWidth = clampWidth(target).coerceAtMost(availableWidth.value)
	}

	fun onDragEnd() {
		dragging = false
		dragWidth?.let { onCommit(it) }
	}

	fun reset() {
		dragWidth = null
		onReset()
	}

	internal fun clearDragUnlessActive() {
		if (!dragging) dragWidth = null
	}
}

@Composable
fun rememberHdResizeHandleState(
	persistedWidth: Float,
	availableWidth: Dp,
	clampWidth: (Float) -> Float,
	onCommit: (Float) -> Unit,
	onReset: () -> Unit,
): HdResizeHandleState {
	val state = remember { HdResizeHandleState() }
	state.clampWidth = clampWidth
	state.onCommit = onCommit
	state.onReset = onReset
	state.persistedWidth = persistedWidth
	state.availableWidth = availableWidth
	// The persisted value catching up to a committed drag releases the local override;
	// skipped while a drag is active so a late settings echo can't snap the column mid-gesture.
	LaunchedEffect(persistedWidth) { state.clearDragUnlessActive() }
	return state
}

/**
 * A full-height gutter on the start edge of a centered, width-capped column
 * that lets the user drag the column wider or narrower. The grip glyph sits
 * subdued until hovered or dragged; the pointer becomes a horizontal-resize
 * cursor on desktop. Double-click resets. The hit strip is wider than the
 * visual gutter so touch drags can land.
 *
 * ```
 *   ┆▪ ▪┆ ┌────────────────────┐
 *   ┆▪ ▪┆ │  centered column   │
 *   ┆▪ ▪┆ └────────────────────┘
 * ```
 *
 * [onOutwardDrag] reports deltas as outward-positive (away from the column's
 * center) regardless of layout direction, so callers stay RTL-safe. A centered
 * column grows by twice the outward delta. Pair with [rememberHdResizeHandleState],
 * which owns that math plus commit-on-drag-end.
 */
@Composable
fun HdResizeHandle(
	onOutwardDrag: (Dp) -> Unit,
	onDragEnd: () -> Unit,
	onReset: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val density = LocalDensity.current
	val layoutDirection = LocalLayoutDirection.current
	val interactionSource = remember { MutableInteractionSource() }
	val hovered by interactionSource.collectIsHoveredAsState()
	val dragged by interactionSource.collectIsDraggedAsState()
	val glyphAlpha = animateFloatAsState(
		if (hovered || dragged) 1f else RESIZE_HANDLE_REST_ALPHA
	)
	val resizeCursor = remember { horizontalResizePointerIcon() }

	Box(
		modifier = modifier
			.fillMaxHeight()
			.width(RESIZE_HANDLE_WIDTH),
		contentAlignment = Alignment.Center,
	) {
		// requiredWidth overflows the 16dp layout slot without shifting the column.
		Box(
			modifier = Modifier
				.requiredWidth(RESIZE_HANDLE_TOUCH_WIDTH)
				.fillMaxHeight()
				.testTag(HD_RESIZE_HANDLE_TAG)
				.hoverable(interactionSource)
				.pointerHoverIcon(resizeCursor)
				.draggable(
					orientation = Orientation.Horizontal,
					state = rememberDraggableState { deltaPx ->
						val outwardPx = if (layoutDirection == LayoutDirection.Ltr) -deltaPx else deltaPx
						onOutwardDrag(with(density) { outwardPx.toDp() })
					},
					interactionSource = interactionSource,
					onDragStopped = { onDragEnd() },
				)
				.pointerInput(onReset) {
					detectTapGestures(onDoubleTap = { onReset() })
				},
			contentAlignment = Alignment.Center,
		) {
			HdDragHandle(modifier = Modifier.graphicsLayer { alpha = glyphAlpha.value })
		}
	}
}

expect fun horizontalResizePointerIcon(): PointerIcon
