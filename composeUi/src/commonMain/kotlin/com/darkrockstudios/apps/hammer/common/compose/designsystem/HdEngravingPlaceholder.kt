package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * Placeholder used when an entity (encyclopedia entry, character, etc.) has
 * no image. Renders a low-contrast 45° hatched stripe pattern with a
 * centered mono label:
 *
 *     [ENGRAVING · NAME]
 *
 * The hatching uses [MaterialTheme.colorScheme.outlineVariant] so it sits
 * on top of any surface tone and respects the project theme override.
 */
@Composable
fun HdEngravingPlaceholder(
	label: String,
	modifier: Modifier = Modifier,
) {
	val stripeColor = MaterialTheme.colorScheme.outlineVariant
	val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

	Box(
		modifier = modifier
			.background(backgroundColor)
			.drawWithCache {
				val strokeWidth = 1.dp.toPx()
				val diagonal = sqrt(size.width * size.width + size.height * size.height)
				val step = 12.dp.toPx()
				onDrawBehind {
					var offset = -diagonal
					while (offset < diagonal) {
						drawLine(
							color = stripeColor,
							start = Offset(offset, 0f),
							end = Offset(offset + size.height, size.height),
							strokeWidth = strokeWidth,
							cap = StrokeCap.Square,
						)
						offset += step
					}
				}
			},
		contentAlignment = Alignment.Center,
	) {
		HdMonoLabel(
			text = "[ENGRAVING · $label]",
			color = MaterialTheme.colorScheme.outline,
		)
	}
}
