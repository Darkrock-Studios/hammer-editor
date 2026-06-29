package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
 * When [tooltipText] is provided, each bar surfaces the underlying value:
 * hover on desktop, tap on touch. A tapped tooltip persists until the user
 * taps elsewhere. Otherwise it stays a pure "manuscript" visual — no axes,
 * no grid, no animation.
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
				val fraction = (item.value.toFloat() / maxValue).coerceIn(0.02f, 1f)
				val barHeight = height * fraction
				if (tooltipText != null) {
					// TooltipBox wraps its anchor in an unmodified Box, so the
					// column weight has to live on a wrapper, not on TooltipBox.
					Box(modifier = Modifier.weight(1f)) {
						BarWithTooltip(
							barHeight = barHeight,
							color = color,
							text = tooltipText(item),
						)
					}
				} else {
					Box(
						modifier = Modifier
							.weight(1f)
							.height(barHeight)
							.clip(BarShape)
							.background(color),
					)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarWithTooltip(
	barHeight: Dp,
	color: Color,
	text: String,
) {
	val state = rememberTooltipState(isPersistent = true)
	val scope = rememberCoroutineScope()
	TooltipBox(
		positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
		tooltip = { PlainTooltip { Text(text) } },
		state = state,
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(barHeight)
				.clip(BarShape)
				.background(color)
				.clickable(
					interactionSource = remember { MutableInteractionSource() },
					indication = null,
				) { scope.launch { state.show() } },
		)
	}
}
