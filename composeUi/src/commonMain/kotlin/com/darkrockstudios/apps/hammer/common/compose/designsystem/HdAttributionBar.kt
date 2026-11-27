package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.util.formatDecimalSeparator

private val BarShape = RoundedCornerShape(2.dp)

/**
 * One row of the "Characters by Appearances" chart:
 * `Alice ▓▓▓▓▓▓▓░░ 15`. Bar fills [fraction] of the track width.
 * [valueWidth] keeps the value column the same width across rows; null wraps it.
 */
@Composable
fun HdAttributionBar(
	label: String,
	value: String,
	fraction: Float,
	color: Color,
	modifier: Modifier = Modifier,
	labelIndent: Dp = 0.dp,
	valueWidth: Dp? = null,
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
			modifier = Modifier.fillMaxWidth(0.28f).padding(start = labelIndent),
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
			textAlign = TextAlign.End,
			modifier = Modifier
				.padding(start = 4.dp)
				.let { if (valueWidth != null) it.width(valueWidth) else it },
			maxLines = 1,
			softWrap = false,
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
	val values = remember(items) { items.map { it.value.formatDecimalSeparator() } }
	val valueStyle = MaterialTheme.typography.titleSmall
	val textMeasurer = rememberTextMeasurer()
	val density = LocalDensity.current
	val valueWidth = remember(values, valueStyle, density) {
		val widest = values.maxOf { textMeasurer.measure(it, valueStyle, softWrap = false).size.width }
		with(density) { widest.toDp() }
	}
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		items.forEachIndexed { index, item ->
			val onClick = item.onClick
			val rowModifier = Modifier
				.fillMaxWidth()
				.let { if (onClick != null) it.hdInteractiveRow(onClick = onClick) else it }
			HdAttributionBar(
				label = item.label,
				value = values[index],
				fraction = item.value.toFloat() / effectiveMax,
				color = item.color,
				modifier = rowModifier,
				labelIndent = item.labelIndent,
				valueWidth = valueWidth,
			)
		}
	}
}

data class HdAttributionItem(
	val label: String,
	val value: Int,
	val color: Color,
	val onClick: (() -> Unit)? = null,
	val labelIndent: Dp = 0.dp,
)
