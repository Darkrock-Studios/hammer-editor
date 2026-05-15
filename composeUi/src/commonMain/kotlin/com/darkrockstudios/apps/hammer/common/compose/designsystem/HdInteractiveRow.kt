package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Marks a row as interactive without an explicit chevron. Adds a subtle
 * surface tint on hover and a slightly stronger one on press, then a
 * standard ripple via [clickable]. Used for list rows on the project
 * dashboard where per-row arrows would feel noisy.
 *
 * When [onClick] is null, the modifier is a no-op
 */
@Composable
fun Modifier.hdInteractiveRow(
	onClick: (() -> Unit)?,
	cornerRadius: Dp = 4.dp,
): Modifier {
	if (onClick == null) return this
	return hdInteractiveRowImpl(onClick, cornerRadius)
}

@Composable
private fun Modifier.hdInteractiveRowImpl(
	onClick: () -> Unit,
	cornerRadius: Dp,
): Modifier = composed {
	val interactionSource = remember { MutableInteractionSource() }
	val isHovered by interactionSource.collectIsHoveredAsState()
	val isPressed by interactionSource.collectIsPressedAsState()
	val tint = MaterialTheme.colorScheme.onSurface
	val background = when {
		isPressed -> tint.copy(alpha = 0.10f)
		isHovered -> tint.copy(alpha = 0.05f)
		else -> Color.Transparent
	}
	this
		.clip(RoundedCornerShape(cornerRadius))
		.background(background)
		.clickable(
			interactionSource = interactionSource,
			indication = null,
			onClick = onClick,
		)
}
