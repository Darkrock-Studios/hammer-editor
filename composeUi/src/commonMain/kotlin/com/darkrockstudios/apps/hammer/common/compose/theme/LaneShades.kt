package com.darkrockstudios.apps.hammer.common.compose.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow

/** The three tones a lane colour takes, so abutting band segments always differ. */
enum class LaneShade { Base, TowardSurface, TowardInk }

/** [base] shaded toward the page [surface] or the [ink] drawn on it. */
fun laneShade(base: Color, shade: LaneShade, surface: Color, ink: Color): Color = when (shade) {
	LaneShade.Base -> base
	LaneShade.TowardSurface -> lerp(base, surface, 0.35f)
	LaneShade.TowardInk -> lerp(base, ink, 0.25f)
}

/** WCAG relative luminance, 0 for black to 1 for white. */
fun relativeLuminance(c: Color): Float {
	val srgb = c.convert(ColorSpaces.Srgb)
	return 0.2126f * linear(srgb.red) + 0.7152f * linear(srgb.green) + 0.0722f * linear(srgb.blue)
}

/** HSL hue in degrees, 0 until 360; 0 for a grey. */
fun hueDegrees(c: Color): Float {
	val srgb = c.convert(ColorSpaces.Srgb)
	val r = srgb.red
	val g = srgb.green
	val b = srgb.blue
	val max = maxOf(r, g, b)
	val delta = max - minOf(r, g, b)
	if (delta == 0f) return 0f
	val sector = when (max) {
		r -> ((g - b) / delta).mod(6f)
		g -> (b - r) / delta + 2f
		else -> (r - g) / delta + 4f
	}
	return sector * 60f
}

private fun linear(channel: Float): Float =
	if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)
