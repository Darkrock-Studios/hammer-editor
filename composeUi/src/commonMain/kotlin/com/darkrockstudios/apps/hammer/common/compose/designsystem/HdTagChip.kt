package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
 * which tag is currently filtering. Pass [accent] together with
 * `active = true` to "light up" the chip with a deterministic color: the
 * `#` prefix becomes a small filled swatch, the border takes the accent,
 * and the fill is a tinted wash. When [onRemove] is non-null an `×`
 * affordance is appended after the label.
 *
 *     ┌───────────┐     ┌─────────────┐     ┌─────────────┐
 *     │ # animal  │     │ # animal × │     │ ▪ animal × │
 *     └───────────┘     └─────────────┘     └─────────────┘
 *        idle              active              lit (accent)
 */
@Composable
fun HdTagChip(
	label: String,
	modifier: Modifier = Modifier,
	onClick: (() -> Unit)? = null,
	onRemove: (() -> Unit)? = null,
	active: Boolean = false,
	accent: Color? = null,
) {
	val litAccent = accent.takeIf { active }
	val borderColor = when {
		litAccent != null -> litAccent
		active -> MaterialTheme.colorScheme.outline
		else -> MaterialTheme.colorScheme.outlineVariant
	}
	val background = when {
		litAccent != null -> litAccent.copy(alpha = 0.15f)
		active -> MaterialTheme.colorScheme.surfaceContainerHigh
		else -> Color.Transparent
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
			.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
			.padding(start = 6.dp, end = if (onRemove != null) 2.dp else 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(5.dp),
	) {
		if (litAccent != null) {
			Box(
				modifier = Modifier
					.size(7.dp)
					.background(litAccent, RectangleShape),
			)
		} else {
			Text(
				text = "#",
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = labelColor,
		)
		if (onRemove != null) {
			Box(
				modifier = Modifier
					.size(18.dp)
					.clickable(onClick = onRemove),
				contentAlignment = Alignment.Center,
			) {
				Text(
					text = "×",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}
