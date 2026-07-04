package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small × clear/remove affordance drawn as two strokes. A font-rendered "×" centers on its text
 * box — which includes the font's descent — so the glyph always sits visibly low inside a small
 * hit target; drawing it keeps it geometrically centered at any size.
 *
 * Pass `onClick = null` when a parent already handles the click (e.g. a chip whose whole row
 * dismisses) and the × is purely visual.
 */
@Composable
fun HdClearGlyph(
	onClick: (() -> Unit)?,
	modifier: Modifier = Modifier,
	boxSize: Dp = 20.dp,
	glyphSize: Dp = 7.dp,
	color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
	contentDescription: String? = null,
) {
	Box(
		modifier = modifier
			.size(boxSize)
			.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
			.then(
				if (contentDescription != null) {
					Modifier.semantics {
						role = Role.Button
						this.contentDescription = contentDescription
					}
				} else Modifier
			),
		contentAlignment = Alignment.Center,
	) {
		Canvas(modifier = Modifier.size(glyphSize)) {
			val stroke = 1.dp.toPx()
			drawLine(
				color = color,
				start = Offset(0f, 0f),
				end = Offset(size.width, size.height),
				strokeWidth = stroke,
				cap = StrokeCap.Square,
			)
			drawLine(
				color = color,
				start = Offset(size.width, 0f),
				end = Offset(0f, size.height),
				strokeWidth = stroke,
				cap = StrokeCap.Square,
			)
		}
	}
}
