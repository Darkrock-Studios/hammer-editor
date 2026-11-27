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
 * [valueWidth] keeps the value column the same width across rows; null sizes it to its content.
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

/** Width of the widest of [values] in the value column's style, for lining up separate charts. */
@Composable
fun rememberHdBarValueWidth(values: List<String>): Dp {
	val style = MaterialTheme.typography.titleSmall
	val textMeasurer = rememberTextMeasurer()
	val density = LocalDensity.current
	return remember(values, style, density) {
		val widest = values.maxOfOrNull { textMeasurer.measure(it, style, softWrap = false).size.width } ?: 0
		with(density) { widest.toDp() }
	}
}

/**
 * Vertical stack of [HdAttributionBar] rows scaled relative to the
 * largest value unless [maxValue] is provided. Charts stacked to be compared
 * should share [maxValue] and a [valueWidth] from [rememberHdBarValueWidth],
 * or their bar tracks differ in length.
 */
@Composable
fun HdMiniBarChart(
	items: List<HdAttributionItem>,
	modifier: Modifier = Modifier,
	maxValue: Int? = null,
	valueWidth: Dp? = null,
) {
	if (items.isEmpty()) return
	val effectiveMax = (maxValue ?: items.maxOf { it.value }).coerceAtLeast(1)
	val values = remember(items) { items.map { it.value.formatDecimalSeparator() } }
	val columnWidth = valueWidth ?: rememberHdBarValueWidth(values)
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
				valueWidth = columnWidth,
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
