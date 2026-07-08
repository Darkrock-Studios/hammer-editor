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
 * Dock-to-top "collapse" affordance: an upward arrow meeting a bar. Use it to dismiss an
 * expandable strip that tucks back up into the chrome above (e.g. a revealed search bar) —
 * distinct from [HdClearGlyph]'s ×, which empties a value. Keeping the two glyphs different
 * matters most when they sit side by side, as in a search strip with both clear and close.
 */
@Composable
fun HdCollapseGlyph(
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	boxSize: Dp = 24.dp,
	glyphSize: Dp = 10.dp,
	color: Color = MaterialTheme.colorScheme.onSurface,
	contentDescription: String? = null,
) {
	Box(
		modifier = modifier
			.size(boxSize)
			.clickable(onClick = onClick)
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
			val w = size.width
			val h = size.height
			drawLine(
				color = color,
				start = Offset(0f, 0f),
				end = Offset(w, 0f),
				strokeWidth = stroke,
				cap = StrokeCap.Square,
			)
			val tipY = h * 0.3f
			drawLine(
				color = color,
				start = Offset(w / 2f, tipY),
				end = Offset(w / 2f, h),
				strokeWidth = stroke,
				cap = StrokeCap.Square,
			)
			drawLine(
				color = color,
				start = Offset(w / 2f, tipY),
				end = Offset(w * 0.15f, h * 0.62f),
				strokeWidth = stroke,
				cap = StrokeCap.Square,
			)
			drawLine(
				color = color,
				start = Offset(w / 2f, tipY),
				end = Offset(w * 0.85f, h * 0.62f),
				strokeWidth = stroke,
				cap = StrokeCap.Square,
			)
		}
	}
}
