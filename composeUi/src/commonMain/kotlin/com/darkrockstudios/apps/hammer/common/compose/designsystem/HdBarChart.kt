package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BarShape = RoundedCornerShape(2.dp)

data class HdBarChartItem(
	val label: String,
	val value: Int,
)

/**
 * Flat vertical bar chart used for per-chapter / per-scene tallies in the
 * dashboard. Bars share a single [color] (defaults to theme primary) and
 * scale relative to the largest value.
 *
 * When [tooltipText] is provided, each column becomes a hover/long-press
 * target that surfaces the underlying value. Otherwise it stays a pure
 * "manuscript" visual — no axes, no grid, no animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HdBarChart(
	items: List<HdBarChartItem>,
	modifier: Modifier = Modifier,
	height: Dp = 140.dp,
	color: Color = MaterialTheme.colorScheme.primary,
	barSpacing: Dp = 4.dp,
	showLabels: Boolean = true,
	tooltipText: ((HdBarChartItem) -> String)? = null,
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
				val fraction = (item.value.toFloat() / maxValue).coerceAtLeast(0.02f)
				if (tooltipText != null) {
					TooltipBox(
						positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
						tooltip = { PlainTooltip { Text(tooltipText(item)) } },
						state = rememberTooltipState(),
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight(),
					) {
						Box(
							modifier = Modifier.fillMaxSize(),
							contentAlignment = Alignment.BottomCenter,
						) {
							Bar(fraction = fraction, color = color, modifier = Modifier.fillMaxWidth())
						}
					}
				} else {
					Bar(fraction = fraction, color = color, modifier = Modifier.weight(1f))
				}
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

@Composable
private fun Bar(fraction: Float, color: Color, modifier: Modifier) {
	Box(
		modifier = modifier
			.fillMaxHeight(fraction)
			.clip(BarShape)
			.background(color),
	)
}
