package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Headline stat block — the foundational dashboard tile:
 *
 *     TOTAL WORDS
 *     23,214
 *     ≈ 93 min reading · 77 pages
 *
 * - [label] renders as mono small-caps (auto-uppercased).
 * - [value] is the big numeric display — defaults to `displayLarge`
 *   (light weight, 64sp) but caller can override.
 * - [subtitle] is optional bodySmall metadata under the value.
 * - [valueColor] lets callers tint the numeric (e.g. primary for `+4,128`,
 *   onSurface for neutral counts).
 * - [content] slot lets callers append progress bars, secondary readouts,
 *   delta badges, etc. — anything that hangs below the stat itself.
 */
@Composable
fun HdStatBlock(
	label: String,
	value: String,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	valueStyle: TextStyle = MaterialTheme.typography.displayLarge,
	valueColor: Color = MaterialTheme.colorScheme.onSurface,
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
		Text(
			text = value,
			style = valueStyle,
			color = valueColor,
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
 * Two-column inline stat — used inside larger blocks where space is tight
 * (e.g. the `Today  ·  847` lines under This Week). Numeric uses
 * `titleMedium` rather than `displayLarge`.
 */
@Composable
fun HdInlineStat(
	label: String,
	value: String,
	modifier: Modifier = Modifier,
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
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}
