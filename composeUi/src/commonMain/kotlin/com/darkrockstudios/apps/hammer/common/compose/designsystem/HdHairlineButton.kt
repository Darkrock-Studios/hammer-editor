package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * Square-cornered text button, same shape vocabulary as [HdTagChip] and the segmented type
 * picker. Plain, it is a hairline frame.
 *
 * A colored button is filled, so the action stands apart by more than hue. [emphasised] fills
 * with the theme's primary color for the action a surface exists for (Save, Create); [danger]
 * fills with the error color when that action destroys something, and takes precedence. One
 * colored button per surface; every other action stays plain. Disabled, it falls back to the
 * plain hairline.
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
	val colors = MaterialTheme.colorScheme
	val showFill = (emphasised || danger) && enabled
	val activeBorder = when {
		danger -> colors.error
		emphasised -> colors.primary
		else -> colors.outlineVariant
	}
	val activeLabel = when {
		danger -> colors.onError
		emphasised -> colors.onPrimary
		else -> colors.onSurface
	}
	val borderColor = if (enabled) activeBorder else colors.outlineVariant
	val labelColor = if (enabled) activeLabel else colors.onSurfaceVariant
	Box(
		modifier = modifier
			.heightIn(min = 32.dp)
			.then(if (showFill) Modifier.background(activeBorder, RectangleShape) else Modifier)
			.hdControlFrame(borderColor)
			.clickable(enabled = enabled, onClick = onClick)
			.padding(horizontal = Ui.Padding.L, vertical = 6.dp),
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelLarge,
			color = labelColor,
			maxLines = 1,
			softWrap = false,
		)
	}
}
