package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.ui.graphics.Color

/** [count] colours spaced evenly around the hue wheel, starting at red. */
fun hdHuePalette(
	count: Int,
	saturation: Float = 0.5f,
	lightness: Float = 0.5f,
): List<Color> {
	if (count <= 0) return emptyList()
	val step = 360f / count
	return List(count) { Color.hsl(step * it, saturation, lightness) }
}
