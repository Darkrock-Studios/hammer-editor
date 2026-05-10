package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * Square-cornered hairline-bordered text button. The design system's
 * answer to Material's `TextButton` / `OutlinedButton` — same shape
 * vocabulary as [HdTagChip] and the segmented type picker.
 *
 * [emphasised] swaps the border + label to the theme's primary color
 * for the action button in a pair (Save/Cancel, Create/Cancel).
 * [danger] swaps to the theme's error color for destructive actions
 * (Remove Server, Delete Project) and takes precedence over emphasised.
 */
@Composable
fun HdHairlineButton(
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	emphasised: Boolean = false,
	danger: Boolean = false,
	enabled: Boolean = true,
) {
	val activeBorder = when {
		danger -> MaterialTheme.colorScheme.error
		emphasised -> MaterialTheme.colorScheme.primary
		else -> MaterialTheme.colorScheme.outlineVariant
	}
	val activeLabel = when {
		danger -> MaterialTheme.colorScheme.error
		emphasised -> MaterialTheme.colorScheme.primary
		else -> MaterialTheme.colorScheme.onSurface
	}
	val borderColor = if (enabled) activeBorder else MaterialTheme.colorScheme.outlineVariant
	val labelColor = if (enabled) activeLabel else MaterialTheme.colorScheme.onSurfaceVariant
	Box(
		modifier = modifier
			.heightIn(min = 32.dp)
			.border(width = Dp.Hairline, color = borderColor, shape = RectangleShape)
			.clickable(enabled = enabled, onClick = onClick)
			.padding(horizontal = Ui.Padding.L, vertical = 6.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelLarge,
			color = labelColor,
		)
	}
}
