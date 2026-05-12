package com.darkrockstudios.apps.hammer.android.widgets

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as SingleColorProvider
import kotlin.math.absoluteValue

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
	SingleColorProvider(color)

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

/** 3-letter uppercase tag from the project name (letters/digits only). "ANY" for the un-bound widget. */
internal fun projectTag(projectName: String?): String {
	if (projectName.isNullOrBlank()) return "ANY"
	val letters = projectName.filter { it.isLetterOrDigit() }.take(3).uppercase()
	return letters.ifBlank { "ANY" }
}
