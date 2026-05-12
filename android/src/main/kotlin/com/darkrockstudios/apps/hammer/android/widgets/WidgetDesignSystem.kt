package com.darkrockstudios.apps.hammer.android.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Editorial / folio palette ported from the Claude Design "Hammer · Android
 * Widgets" mock. Lives next to the widget code because Glance can't share
 * Compose composables with the main composeUi design system.
 */
internal data class WidgetColors(
	val surfaceContainerLow: Color,
	val onSurface: Color,
	val onSurfaceVariant: Color,
	val onSurfaceMuted: Color,
	val onSurfaceDim: Color,
	val rule: Color,
	val ruleStrong: Color,
	val ruleSoft: Color,
)

internal val lightWidgetColors = WidgetColors(
	surfaceContainerLow = Color(0xFFF5F0E9),
	onSurface = Color(0xFF1C1B1F),
	onSurfaceVariant = Color(0xFF49454F),
	onSurfaceMuted = Color(0xFF79747E),
	onSurfaceDim = Color(0xFF9C9AA1),
	rule = Color(0xFFC9C2B6),
	ruleStrong = Color(0xFFA8A294),
	ruleSoft = Color(0xFFE2DCD0),
)

internal val darkWidgetColors = WidgetColors(
	surfaceContainerLow = Color(0xFF1D1B20),
	onSurface = Color(0xFFE6E0E9),
	onSurfaceVariant = Color(0xFFCAC4D0),
	onSurfaceMuted = Color(0xFF938F99),
	onSurfaceDim = Color(0xFF6E6A75),
	rule = Color(0xFF3A3740),
	ruleStrong = Color(0xFF534F5A),
	ruleSoft = Color(0xFF2A282F),
)

/** Day/night [androidx.glance.unit.ColorProvider] from the paired token in [lightWidgetColors] / [darkWidgetColors]. */
internal fun widgetColor(pick: (WidgetColors) -> Color): androidx.glance.unit.ColorProvider =
	ColorProvider(day = pick(lightWidgetColors), night = pick(darkWidgetColors))

internal fun singleWidgetColor(color: Color): androidx.glance.unit.ColorProvider =
	ColorProvider(day = color, night = color)

/** Fallback stripe color when a project has no custom theme — the design's HAMMER_LIGHT.primary purple. */
internal val FallbackAccent: Color = Color(0xFF6750A4)

/** Parse a hex string like "#RRGGBB" or "#AARRGGBB". Returns [FallbackAccent] for null / malformed input. */
internal fun parseAccent(hex: String?): Color {
	if (hex.isNullOrBlank()) return FallbackAccent
	return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(FallbackAccent)
}

/** Stable, name-derived 2-digit folio number. Pure: same name → same number. */
internal fun stableProjectNumber(projectName: String?): String {
	if (projectName.isNullOrBlank()) return "??"
	val n = projectName.hashCode().absoluteValue % 100
	return n.toString().padStart(2, '0')
}

internal fun monoMicroStyle(
	color: androidx.glance.unit.ColorProvider,
	size: TextUnit = 9.sp,
): TextStyle = TextStyle(
	color = color,
	fontSize = size,
	fontFamily = FontFamily.Monospace,
	fontWeight = FontWeight.Medium,
)

internal fun monoLabelStyle(
	color: androidx.glance.unit.ColorProvider,
	size: TextUnit = 10.sp,
): TextStyle = TextStyle(
	color = color,
	fontSize = size,
	fontFamily = FontFamily.Monospace,
	fontWeight = FontWeight.Medium,
)

@Composable
internal fun WidgetHairline(
	color: androidx.glance.unit.ColorProvider,
	modifier: GlanceModifier = GlanceModifier,
) {
	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(1.dp)
			.background(color),
	) {}
}

/**
 * Word-count formatter shared across widgets. Renders as a 2-digit truncated
 * "k" once we cross 1k, "M" past a million; null gets an em-dash.
 */
internal fun formatWidgetWords(words: Int?): String = when {
	words == null -> "—"
	words >= 1_000_000 -> "%.1fM".format(words / 1_000_000.0)
	words >= 10_000 -> "%.0fk".format(words / 1_000.0)
	words >= 1_000 -> "%.1fk".format(words / 1_000.0)
	else -> words.toString()
}

/**
 * Hairline progress bar. Glance has no fractional-width modifier, so the bar
 * is rendered as N equal-weight segments — each one fill or track based on the
 * rounded percentage. Glance caps Row children at 10, so 10 is the max.
 */
@Composable
internal fun WidgetProgressBar(
	value: Int,
	max: Int,
	fillColor: androidx.glance.unit.ColorProvider,
	trackColor: androidx.glance.unit.ColorProvider = widgetColor { it.ruleSoft },
	heightDp: Int = 4,
	segments: Int = 10,
) {
	val cappedSegments = segments.coerceAtMost(10)
	val pct = if (max > 0) (value.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
	val filled = (pct * cappedSegments).roundToInt().coerceIn(0, cappedSegments)
	Row(
		modifier = GlanceModifier
			.fillMaxWidth()
			.height(heightDp.dp)
			.background(trackColor),
	) {
		repeat(cappedSegments) { i ->
			Box(
				modifier = GlanceModifier
					.defaultWeight()
					.fillMaxHeight()
					.background(if (i < filled) fillColor else trackColor),
			) {}
		}
	}
}

/**
 * 7-day session sparkline. Each value renders as a vertical bar of dp height
 * proportional to the peak; zero values draw a 1dp baseline tick in the soft
 * rule color so the grid stays legible. Bar gap is faked with right-padding so
 * we keep the Row at one child per value (Glance caps Row children at 10).
 */
@Composable
internal fun WidgetSparkline(
	values: List<Int>,
	fillColor: androidx.glance.unit.ColorProvider,
	emptyColor: androidx.glance.unit.ColorProvider = widgetColor { it.ruleSoft },
	heightDp: Int = 28,
) {
	val peak = max(values.maxOrNull() ?: 1, 1)
	Row(
		modifier = GlanceModifier
			.fillMaxWidth()
			.height(heightDp.dp),
	) {
		values.forEachIndexed { i, v ->
			val barH = if (v > 0) {
				max(2, (v.toFloat() / peak * heightDp).roundToInt()).coerceAtMost(heightDp)
			} else 1
			val cellPadding = if (i < values.lastIndex) 2.dp else 0.dp
			Box(
				modifier = GlanceModifier
					.defaultWeight()
					.fillMaxHeight()
					.padding(end = cellPadding),
				contentAlignment = Alignment.BottomCenter,
			) {
				Box(
					modifier = GlanceModifier
						.fillMaxWidth()
						.height(barH.dp)
						.background(if (v > 0) fillColor else emptyColor),
				) {}
			}
		}
	}
}
