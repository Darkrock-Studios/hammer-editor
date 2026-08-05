package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val RailWidth = 10.dp

/** Strip reserved for the edge rule; the thumb covers the rest of the rail exactly. */
private val RuleInset = 1.dp
private val ThumbThickness = RailWidth - RuleInset
private val HatchSpacing = 5.dp

actual val MpScrollBarGutter: Dp = RailWidth

@Composable
actual fun MpScrollBarList(
	modifier: Modifier,
	state: LazyListState
) {
	ScrollbarRail(modifier, state.isScrollable, rememberScrollbarAdapter(state))
}

@Composable
actual fun MpScrollBarColumn(
	modifier: Modifier,
	state: ScrollState
) {
	// A ScrollState that was never attached to a scrolling container reports maxValue as
	// Int.MAX_VALUE, which would otherwise read as "scrollable" and draw a rail over nothing.
	val scrollable = state.maxValue != Int.MAX_VALUE && state.isScrollable
	ScrollbarRail(modifier, scrollable, rememberScrollbarAdapter(state))
}

@Composable
actual fun MpScrollBarGrid(
	modifier: Modifier,
	state: LazyGridState
) {
	ScrollbarRail(modifier, state.isScrollable, rememberScrollbarAdapter(state))
}

@Composable
actual fun MpScrollBarStaggeredGrid(
	modifier: Modifier,
	state: LazyStaggeredGridState
) {
	ScrollbarRail(
		modifier = modifier,
		scrollable = state.isScrollable,
		adapter = remember(state) { StaggeredGridScrollbarAdapter(state) },
	)
}

/**
 * Gutter the scrollbar lives in: a vertical `outlineVariant` hairline marking the scrollable edge,
 * with a hatched track inside it that the thumb masks as it travels. Both are drawn only while
 * there is somewhere to scroll, so screens that fit get no chrome at all.
 *
 * ```
 * │╱╱╱│
 * │███│  <- thumb, masking the hatch
 * │╱╱╱│
 *  ^ edge rule
 * ```
 */
@Composable
private fun ScrollbarRail(
	modifier: Modifier,
	scrollable: Boolean,
	adapter: ScrollbarAdapter,
) {
	val colorScheme = MaterialTheme.colorScheme
	val ruleColor = colorScheme.outlineVariant
	val hatchColor = ruleColor.copy(alpha = 0.55f)
	val style = remember(colorScheme) {
		ScrollbarStyle(
			minimalHeight = 24.dp,
			thickness = ThumbThickness,
			shape = RectangleShape,
			hoverDurationMillis = 220,
			unhoverColor = colorScheme.outlineVariant,
			hoverColor = colorScheme.outline,
		)
	}
	Box(
		modifier = modifier
			.width(RailWidth)
			.drawWithCache {
				val stroke = Stroke(width = Dp.Hairline.toPx().coerceAtLeast(1f))
				val rule = Path().apply {
					val x = stroke.width / 2f
					moveTo(x, 0f)
					lineTo(x, size.height)
				}
				val hatch = hatchPath(
					inset = RuleInset.toPx(),
					spacing = HatchSpacing.toPx(),
					width = size.width,
					height = size.height,
				)
				onDrawBehind {
					if (!scrollable) return@onDrawBehind
					drawPath(rule, ruleColor, style = stroke)
					drawPath(hatch, hatchColor, style = stroke)
				}
			},
		contentAlignment = Alignment.CenterEnd,
	) {
		VerticalScrollbar(
			adapter = adapter,
			modifier = Modifier.fillMaxHeight(),
			style = style,
		)
	}
}

/**
 * 45 degree hatching for the track, built once per size rather than a line per draw pass. Segments
 * are clamped to the track rather than clipped, so no off-screen geometry is submitted.
 */
private fun hatchPath(inset: Float, spacing: Float, width: Float, height: Float): Path {
	val path = Path()
	if (spacing <= 0f || width <= inset || height <= 0f) return path
	// Each line runs bottom-left to top-right: y = height - (x - origin).
	var origin = inset - height
	while (origin < width) {
		val x1 = maxOf(inset, origin)
		val x2 = minOf(width, origin + height)
		if (x2 > x1) {
			path.moveTo(x1, height - (x1 - origin))
			path.lineTo(x2, height - (x2 - origin))
		}
		origin += spacing
	}
	return path
}

/**
 * Compose ships no scrollbar adapter for staggered grids, but the state exposes the same metrics
 * the built-in adapters are built from. Dragging scrolls by pixel delta rather than snapping.
 */
private class StaggeredGridScrollbarAdapter(
	private val state: LazyStaggeredGridState,
) : ScrollbarAdapter {

	override val viewportSize: Double
		get() = state.scrollIndicatorState?.viewportSize?.toDouble() ?: 0.0

	// Matching the viewport means "nothing to scroll", which is the right reading before measurement.
	override val contentSize: Double
		get() = state.scrollIndicatorState?.contentSize?.toDouble() ?: viewportSize

	override val scrollOffset: Double
		get() = state.scrollIndicatorState?.scrollOffset?.toDouble() ?: 0.0

	override suspend fun scrollTo(scrollOffset: Double) {
		state.scrollBy((scrollOffset - this.scrollOffset).toFloat())
	}
}
