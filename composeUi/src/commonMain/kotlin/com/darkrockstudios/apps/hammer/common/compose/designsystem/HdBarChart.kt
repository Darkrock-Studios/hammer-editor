package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class HdBarChartItem(
	val label: String,
	val value: Int,
)

/**
 * Flat vertical bar chart used for per-chapter / per-scene tallies in the
 * dashboard. Bars share a single [color] (defaults to theme primary) and
 * scale relative to the largest value.
 *
 * Designed to match the dashboard mock — no axes, no grid, no animation.
 * For richer interactive charts (tooltips, multi-series) use KoalaPlot
 * directly; this is the "manuscript" treatment.
 */
@Composable
fun HdBarChart(
	items: List<HdBarChartItem>,
	modifier: Modifier = Modifier,
	height: Dp = 140.dp,
	color: Color = MaterialTheme.colorScheme.primary,
	barSpacing: Dp = 4.dp,
	showLabels: Boolean = true,
) {
	if (items.isEmpty()) return
	val maxValue = items.maxOf { it.value }.coerceAtLeast(1)

	Column(modifier = modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(height),
			horizontalArrangement = Arrangement.spacedBy(barSpacing),
			verticalAlignment = Alignment.Bottom,
		) {
			items.forEach { item ->
				val fraction = item.value.toFloat() / maxValue
				Box(
					modifier = Modifier
						.weight(1f)
						.fillMaxHeight(fraction.coerceAtLeast(0.02f))
						.clip(RoundedCornerShape(2.dp))
						.background(color),
				)
			}
		}
		if (showLabels) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(barSpacing),
			) {
				items.forEach { item ->
					Text(
						text = item.label,
						modifier = Modifier.weight(1f),
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center,
					)
				}
			}
		}
	}
}
