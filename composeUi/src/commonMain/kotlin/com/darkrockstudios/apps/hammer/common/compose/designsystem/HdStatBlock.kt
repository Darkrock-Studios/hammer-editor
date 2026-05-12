package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Headline stat block — the dashboard's foundational tile.
 *
 *     TOTAL WORDS
 *     23,214
 *     ≈ 93 min reading · 77 pages
 *
 * The [content] slot hangs below for progress bars, delta badges, or
 * inline secondary stats.
 */
@Composable
fun HdStatBlock(
	label: String,
	value: String,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	valueStyle: TextStyle = MaterialTheme.typography.displayLarge,
	valueColor: Color = MaterialTheme.colorScheme.onSurface,
	valueMaxLines: Int = 1,
	content: @Composable ColumnScope.() -> Unit = {},
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		HdMonoLabel(
			text = label,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		val resolvedFontSize = valueStyle.fontSize.takeIf { it.isSp } ?: 57.sp
		Text(
			text = value,
			style = valueStyle,
			color = valueColor,
			autoSize = TextAutoSize.StepBased(
				minFontSize = resolvedFontSize * 0.7f,
				maxFontSize = resolvedFontSize,
				stepSize = 1.sp,
			),
			maxLines = valueMaxLines,
		)
		if (subtitle != null) {
			Text(
				text = subtitle,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		content()
	}
}

/**
 * Two-column inline stat: label left, value right (`Today · 847`).
 * Used inside larger blocks where space is tight; pass [valueStyle] to
 * tighten further (e.g. titleSmall for nested metadata rows).
 */
@Composable
fun HdInlineStat(
	label: String,
	value: String,
	modifier: Modifier = Modifier,
	valueStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(vertical = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = value,
			style = valueStyle,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}
