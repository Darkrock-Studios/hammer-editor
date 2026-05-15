package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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

/**
 * Square hairline checkbox — the design system's answer to M3
 * `Checkbox`. Empty when unchecked; primary fill + check glyph when
 * checked. Pair with [HdHairlineToggleRow] for the full row pattern.
 */
@Composable
fun HdHairlineCheckbox(
	checked: Boolean,
	modifier: Modifier = Modifier,
) {
	val borderColor = if (checked) MaterialTheme.colorScheme.primary
	else MaterialTheme.colorScheme.outlineVariant
	val fill = if (checked) MaterialTheme.colorScheme.primary
	else Color.Transparent
	Box(
		modifier = modifier
			.size(18.dp)
			.border(width = Dp.Hairline, color = borderColor, shape = RectangleShape)
			.background(fill, RectangleShape),
		contentAlignment = Alignment.Center,
	) {
		if (checked) {
			Text(
				text = "✓",
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onPrimary,
			)
		}
	}
}
