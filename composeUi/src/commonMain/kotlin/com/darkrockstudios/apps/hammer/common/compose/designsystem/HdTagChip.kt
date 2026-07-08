package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors

/**
 * Hairline-bordered tag chip. The leading glyph is a small filled swatch
 * colored from [accent], which defaults to a deterministic per-label
 * color via [LocalHammerColors]. Pass `accent = null` explicitly to
 * suppress the swatch and show a neutral `#` prefix. Active state fills
 * with `surfaceContainerHigh` and uses a stronger border so the user can
 * see which tag is currently filtering. When [onRemove] is non-null an
 * `×` affordance is appended after the label.
 *
 *     ┌─────────────┐     ┌─────────────┐     ┌───────────┐
 *     │ ▪ animal   │     │ ▪ animal × │     │ # animal  │
 *     └─────────────┘     └─────────────┘     └───────────┘
 *         idle              active             accent=null
 */
@Composable
fun HdTagChip(
	label: String,
	modifier: Modifier = Modifier,
	onClick: (() -> Unit)? = null,
	onRemove: (() -> Unit)? = null,
	active: Boolean = false,
	accent: Color? = LocalHammerColors.current.tagColor(label),
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
			.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
			.padding(start = 6.dp, end = if (onRemove != null) 2.dp else 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(5.dp),
	) {
		if (accent != null) {
			Box(
				modifier = Modifier
					.size(7.dp)
					.background(accent, RectangleShape),
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
			HdClearGlyph(
				onClick = onRemove,
				boxSize = 18.dp,
				glyphSize = 6.dp,
			)
		}
	}
}
