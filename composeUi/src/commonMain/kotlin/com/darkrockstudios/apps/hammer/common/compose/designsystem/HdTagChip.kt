package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hairline-bordered tag chip with a `#` mono prefix. Active state fills
 * with `surfaceContainerHigh` and a stronger border so the user can see
 * which tag is currently filtering.
 *
 *     ┌───────────┐
 *     │ # animal  │
 *     └───────────┘
 */
@Composable
fun HdTagChip(
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	active: Boolean = false,
) {
	val borderColor = if (active) {
		MaterialTheme.colorScheme.outline
	} else {
		MaterialTheme.colorScheme.outlineVariant
	}
	val background = if (active) {
		MaterialTheme.colorScheme.surfaceContainerHigh
	} else {
		Color.Transparent
	}
	val labelColor = if (active) {
		MaterialTheme.colorScheme.onSurface
	} else {
		MaterialTheme.colorScheme.onSurfaceVariant
	}

	Row(
		modifier = modifier
			.height(24.dp)
			.background(background, RectangleShape)
			.border(width = Dp.Hairline, color = borderColor, shape = RectangleShape)
			.clickable(onClick = onClick)
			.padding(start = 6.dp, end = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(5.dp),
	) {
		Text(
			text = "#",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = labelColor,
		)
	}
}
