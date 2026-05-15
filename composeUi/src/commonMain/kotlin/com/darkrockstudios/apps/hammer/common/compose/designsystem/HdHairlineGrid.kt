package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * N-column grid with hairline dividers between rows and columns. Use for
 * paired numeric stats on narrow screens where a single-column stack
 * leaves the layout feeling unstructured.
 *
 *     ┌──────────────┬──────────────┐
 *     │ SCENES       │ AVG / SCENE  │
 *     │ 15           │ 1,547        │
 *     ├──────────────┼──────────────┤
 *     │ NOTES        │ EVENTS       │
 *     │ 2            │ 12           │
 *     └──────────────┴──────────────┘
 *
 * Trailing cells in a partial row leave their slot empty (no padding cell).
 */
@Composable
fun HdHairlineGrid(
	columns: Int,
	modifier: Modifier = Modifier,
	cellPadding: Dp = 12.dp,
	cells: List<@Composable () -> Unit>,
) {
	require(columns > 0) { "columns must be positive" }
	if (cells.isEmpty()) return
	val rows = cells.chunked(columns)
	val dividerColor = MaterialTheme.colorScheme.outlineVariant
	val strokePx = with(LocalDensity.current) { Dp.Hairline.toPx().coerceAtLeast(1f) }
	// Vertical dividers are drawn via drawBehind instead of being placed as
	// child VerticalDividers inside a height(IntrinsicSize.Min) row, because
	// that approach crashes when a cell contains a SubcomposeLayout.
	Column(modifier = modifier.fillMaxWidth()) {
		rows.forEachIndexed { rowIdx, rowCells ->
			if (rowIdx > 0) {
				HorizontalDivider(thickness = Dp.Hairline, color = dividerColor)
			}
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.drawBehind {
						val step = size.width / columns
						for (i in 1 until columns) {
							val x = step * i
							drawLine(
								color = dividerColor,
								start = Offset(x, 0f),
								end = Offset(x, size.height),
								strokeWidth = strokePx,
							)
						}
					},
			) {
				rowCells.forEach { cell ->
					Box(
						modifier = Modifier
							.weight(1f)
							.padding(cellPadding),
					) {
						cell()
					}
				}
				// Pad partial trailing row so cells keep their column width.
				val emptySlots = columns - rowCells.size
				repeat(emptySlots) {
					Box(modifier = Modifier.weight(1f))
				}
			}
		}
	}
}
