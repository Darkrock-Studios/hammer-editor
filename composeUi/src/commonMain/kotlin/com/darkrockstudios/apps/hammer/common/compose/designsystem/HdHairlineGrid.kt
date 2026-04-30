package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
	Column(modifier = modifier.fillMaxWidth()) {
		rows.forEachIndexed { rowIdx, rowCells ->
			if (rowIdx > 0) {
				HorizontalDivider(thickness = Dp.Hairline, color = dividerColor)
			}
			Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
				rowCells.forEachIndexed { colIdx, cell ->
					if (colIdx > 0) {
						VerticalDivider(
							modifier = Modifier.fillMaxHeight(),
							thickness = Dp.Hairline,
							color = dividerColor,
						)
					}
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
