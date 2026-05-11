package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small (30dp) square-cornered hairline-bordered toolbar action — the
 * compact toggle/tool button used in dialog toolbar rows. Caller fills
 * the centered slot with a glyph (see [HdLogGlyph]) or any small
 * indicator. [active] flips the border to `outline` and fills the
 * background with `surfaceContainerHigh` to read as "currently
 * engaged".
 */
@Composable
fun HdToolButton(
	active: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	val borderColor = if (active) {
		MaterialTheme.colorScheme.outline
	} else {
		MaterialTheme.colorScheme.outlineVariant
	}
	val bg = if (active) {
		MaterialTheme.colorScheme.surfaceContainerHigh
	} else {
		Color.Transparent
	}
	Box(
		modifier = modifier
			.size(30.dp)
			.background(bg)
			.border(width = Dp.Hairline, color = borderColor, shape = RectangleShape)
			.clickable(onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		content()
	}
}

/**
 * Three stacked hairlines — the "log / list" glyph used inside an
 * [HdToolButton] to toggle a sync-log tail view.
 */
@Composable
fun HdLogGlyph(modifier: Modifier = Modifier) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(2.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		repeat(3) {
			Box(
				modifier = Modifier
					.width(12.dp)
					.height(1.dp)
					.background(MaterialTheme.colorScheme.onSurfaceVariant),
			)
		}
	}
}
