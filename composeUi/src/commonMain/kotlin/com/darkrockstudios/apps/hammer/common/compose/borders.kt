package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

fun Modifier.leftBorder(strokeWidth: Dp, color: Color) = composed(
	factory = {
		val density = LocalDensity.current

		val strokeWidthPx = density.run { strokeWidth.toPx() }

		Modifier.drawWithCache {
			onDrawWithContent {
				drawContent()

				val height = size.height - strokeWidthPx / 2

				drawLine(
					color = color,
					start = Offset(x = 0f, y = 0f),
					end = Offset(x = 0f, y = height),
					strokeWidth = strokeWidthPx
				)
			}
		}
	}
)

fun Modifier.rightBorder(strokeWidth: Dp, color: Color) = composed(
	factory = {
		val density = LocalDensity.current

		val strokeWidthPx = density.run { strokeWidth.toPx() }

		Modifier.drawWithCache {
			onDrawWithContent {
				drawContent()

				val height = size.width - strokeWidthPx / 2
				val width = size.width - strokeWidthPx / 2

				drawLine(
					color = color,
					start = Offset(x = width, y = 0f),
					end = Offset(x = width, y = height),
					strokeWidth = strokeWidthPx
				)
			}
		}
	}
)