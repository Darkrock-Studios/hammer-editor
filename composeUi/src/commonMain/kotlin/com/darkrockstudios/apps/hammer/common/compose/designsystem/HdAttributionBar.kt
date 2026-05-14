package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator

private val BarShape = RoundedCornerShape(2.dp)

/**
 * One row of the "Characters by Appearances" chart:
 * `Alice ▓▓▓▓▓▓▓░░ 15`. Bar fills [fraction] of the track width.
 */
@Composable
fun HdAttributionBar(
	label: String,
	value: String,
	fraction: Float,
	color: Color,
	modifier: Modifier = Modifier,
) {
	val safeFraction = fraction.coerceIn(0f, 1f)
	Row(
		modifier = modifier.height(20.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.fillMaxWidth(0.28f),
			maxLines = 1,
		)
		Box(
			modifier = Modifier
				.weight(1f)
				.fillMaxHeight()
				.clip(BarShape)
				.background(MaterialTheme.colorScheme.surfaceVariant),
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth(safeFraction)
					.fillMaxHeight()
					.background(color),
			)
		}
		Text(
			text = value,
			style = MaterialTheme.typography.titleSmall,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier
				.fillMaxWidth(0.10f)
				.padding(start = 4.dp),
			maxLines = 1,
		)
	}
}

/**
 * Vertical stack of [HdAttributionBar] rows scaled relative to the
 * largest value unless [maxValue] is provided.
 */
@Composable
fun HdMiniBarChart(
	items: List<HdAttributionItem>,
	modifier: Modifier = Modifier,
	maxValue: Int? = null,
) {
	if (items.isEmpty()) return
	val effectiveMax = (maxValue ?: items.maxOf { it.value }).coerceAtLeast(1)
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		items.forEach { item ->
			HdAttributionBar(
				label = item.label,
				value = item.value.formatDecimalSeparator(),
				fraction = item.value.toFloat() / effectiveMax,
				color = item.color,
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

data class HdAttributionItem(
	val label: String,
	val value: Int,
	val color: Color,
)
