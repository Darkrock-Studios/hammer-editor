package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One nesting level in a tree row: the width of a disclosure column. */
val HdIndentStep: Dp = 16.dp

/**
 * Start of a row's own content at [depth], counting the outer gutter.
 * Depth 1 is a top level row.
 */
fun hdIndentFor(depth: Int, gutter: Dp = HdIndentStep, step: Dp = HdIndentStep): Dp =
	gutter + step * (depth - 1).coerceAtLeast(0)

/**
 * Vertical hairline rails, one per ancestor level, descending through a nested row.
 * Each rail is centered on the disclosure column of the ancestor that owns it, so a
 * child row reads as hanging off its parent's chevron.
 *
 * ```
 * ⌄ Chapter I
 * ┆  ⌄ Scene Group
 * ┆  ┆   Scene name
 * ```
 *
 * [levels] is the number of ancestors above this row: `depth - 1`.
 */
fun Modifier.hdIndentRails(
	levels: Int,
	color: Color,
	gutter: Dp = HdIndentStep,
	step: Dp = HdIndentStep,
): Modifier {
	if (levels <= 0) return this
	return drawBehind {
		val stepPx = step.toPx()
		val centerOffset = gutter.toPx() + (stepPx / 2f)
		repeat(levels) { level ->
			val x = centerOffset + (stepPx * level)
			drawLine(
				color = color,
				start = Offset(x, 0f),
				end = Offset(x, size.height),
				strokeWidth = Dp.Hairline.toPx(),
			)
		}
	}
}
