package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * Two columns of three small squares — the reorder "grip" glyph that
 * marks a row as draggable. Squares (never dots) keep it in the
 * square-cornered vocabulary; reads `onSurfaceVariant` so it stays
 * grayscale chrome.
 *
 * ```
 *   ▪ ▪
 *   ▪ ▪
 *   ▪ ▪
 * ```
 */
@Composable
fun HdDragHandle(
	modifier: Modifier = Modifier,
	color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
	Row(
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(3.dp),
	) {
		repeat(2) {
			Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
				repeat(3) {
					Box(
						modifier = Modifier
							.size(2.dp)
							.background(color, RectangleShape),
					)
				}
			}
		}
	}
}
