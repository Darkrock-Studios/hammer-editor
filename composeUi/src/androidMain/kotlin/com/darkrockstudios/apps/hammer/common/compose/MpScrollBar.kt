package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The Android bar fades in over the content, so it reserves no layout space.
actual val MpScrollBarGutter: Dp = 0.dp

@Composable
actual fun MpScrollBarList(
	modifier: Modifier,
	state: LazyListState
) {
	ScrollbarCanvas(modifier = modifier, isScrollInProgress = state.isScrollInProgress) { viewportSize ->
		val firstVisibleItem = state.layoutInfo.visibleItemsInfo.firstOrNull()
		val firstItemSize = firstVisibleItem?.size ?: 0
		// Estimated from the first visible item, so the knob drifts when item sizes are not uniform.
		// A fixed knob size keeps it from visibly growing and shrinking as you scroll.
		val estimatedContentSize = (firstItemSize * state.layoutInfo.totalItemsCount).toFloat()
		if (estimatedContentSize <= 0f) {
			null
		} else {
			val offset = state.firstVisibleItemIndex * firstItemSize + state.firstVisibleItemScrollOffset
			ScrollbarKnob(
				position = (viewportSize / estimatedContentSize) * offset,
				size = viewportSize * FixedKnobRatio,
			)
		}
	}
}

@Composable
actual fun MpScrollBarColumn(
	modifier: Modifier,
	state: ScrollState
) {
	ScrollbarCanvas(modifier = modifier, isScrollInProgress = state.isScrollInProgress) { viewportSize ->
		val scrollRange = state.maxValue
		// A ScrollState that was never attached to a scrolling container reports Int.MAX_VALUE.
		if (scrollRange <= 0 || scrollRange == Int.MAX_VALUE) {
			null
		} else {
			val contentSize = viewportSize + scrollRange
			ScrollbarKnob(
				position = (viewportSize / contentSize) * state.value,
				size = (viewportSize / contentSize) * viewportSize,
			)
		}
	}
}

@Composable
actual fun MpScrollBarGrid(
	modifier: Modifier,
	state: LazyGridState
) {
	ScrollbarCanvas(modifier = modifier, isScrollInProgress = state.isScrollInProgress) { viewportSize ->
		itemCountKnob(
			viewportSize = viewportSize,
			firstVisibleIndex = state.firstVisibleItemIndex,
			visibleCount = state.layoutInfo.visibleItemsInfo.size,
			totalCount = state.layoutInfo.totalItemsCount,
		)
	}
}

@Composable
actual fun MpScrollBarStaggeredGrid(
	modifier: Modifier,
	state: LazyStaggeredGridState
) {
	ScrollbarCanvas(modifier = modifier, isScrollInProgress = state.isScrollInProgress) { viewportSize ->
		itemCountKnob(
			viewportSize = viewportSize,
			firstVisibleIndex = state.firstVisibleItemIndex,
			visibleCount = state.layoutInfo.visibleItemsInfo.size,
			totalCount = state.layoutInfo.totalItemsCount,
		)
	}
}

/** Knob ratio for lists, whose content extent can only be estimated. */
private const val FixedKnobRatio = 0.1f

/**
 * Knob driven by item counts rather than pixel extents, so it works the same for uniform and
 * staggered grids. It moves one item at a time, fine-grained enough for grids that hold more items
 * than fit on screen.
 */
private fun itemCountKnob(
	viewportSize: Float,
	firstVisibleIndex: Int,
	visibleCount: Int,
	totalCount: Int,
): ScrollbarKnob? = if (totalCount <= 0 || visibleCount <= 0 || visibleCount >= totalCount) {
	null
} else {
	ScrollbarKnob(
		position = viewportSize * (firstVisibleIndex.toFloat() / totalCount),
		size = viewportSize * (visibleCount.toFloat() / totalCount),
	)
}

/** Where the knob sits along the track and how long it is, both in pixels. */
private data class ScrollbarKnob(
	val position: Float,
	val size: Float,
)

/**
 * Vertical scrollbar that appears when the user starts scrolling and fades out once they stop.
 * It is composed of a track and a knob that moves across it; [knob] supplies the knob's position
 * and length for a given viewport, and returns `null` when there is nothing to scroll.
 *
 * The knob is measured inside the draw lambda, so scroll position reads invalidate draw only.
 *
 * @param thickness how thick the track and knob should be
 * @param trackColor colour of the track; [Color.Transparent] hides it
 * @param padding edge padding, so the knob is not flush with the ends of the track
 * @param hiddenAlpha use a non-zero value to keep the scrollbar from fading out completely
 */
@Composable
private fun ScrollbarCanvas(
	modifier: Modifier,
	isScrollInProgress: Boolean,
	thickness: Dp = 4.dp,
	knobCornerRadius: Dp = 4.dp,
	trackCornerRadius: Dp = 2.dp,
	knobColor: Color = MaterialTheme.colorScheme.outline,
	trackColor: Color = Color.Transparent,
	padding: Dp = 0.dp,
	visibleAlpha: Float = 1f,
	hiddenAlpha: Float = 0f,
	fadeInAnimationDurationMs: Int = 150,
	fadeOutAnimationDurationMs: Int = 500,
	fadeOutAnimationDelayMs: Int = 1000,
	knob: (viewportSize: Float) -> ScrollbarKnob?,
) {
	val targetAlpha = if (isScrollInProgress) visibleAlpha else hiddenAlpha
	val animationDurationMs =
		if (isScrollInProgress) fadeInAnimationDurationMs else fadeOutAnimationDurationMs
	val animationDelayMs = if (isScrollInProgress) 0 else fadeOutAnimationDelayMs

	val alpha by animateFloatAsState(
		targetValue = targetAlpha,
		animationSpec = tween(delayMillis = animationDelayMs, durationMillis = animationDurationMs),
	)

	Canvas(
		modifier = modifier
			.width(thickness)
			.fillMaxHeight()
	) {
		if (!isScrollInProgress && alpha <= 0f) return@Canvas

		val thicknessPx = thickness.toPx()
		val paddingPx = padding.toPx()
		val trackLength = size.height - paddingPx * 2
		val measured = knob(trackLength) ?: return@Canvas
		val left = size.width - thicknessPx

		drawRoundRect(
			color = trackColor,
			topLeft = Offset(left, paddingPx),
			size = Size(thicknessPx, trackLength),
			alpha = alpha,
			cornerRadius = CornerRadius(x = trackCornerRadius.toPx(), y = trackCornerRadius.toPx()),
		)

		drawRoundRect(
			color = knobColor,
			topLeft = Offset(left, measured.position + paddingPx),
			size = Size(thicknessPx, measured.size),
			alpha = alpha,
			cornerRadius = CornerRadius(x = knobCornerRadius.toPx(), y = knobCornerRadius.toPx()),
		)
	}
}
