package com.darkrockstudios.apps.hammer.common.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/**
 * Uses [PaletteStyle.Rainbow] so the secondary/tertiary palettes don't
 * collapse toward the primary hue the way [PaletteStyle.TonalSpot] does —
 * Rainbow gives each palette its own slot in the color wheel, which makes
 * the user's two-color choice actually distinguishable on screen. The
 * user's [ProjectTheme.secondary] seeds both Material 3 secondary AND
 * tertiary so accent surfaces (chips, badges, indicators) light up with
 * it. Returns null when either color fails to parse.
 */
fun ProjectTheme.toColorScheme(isDark: Boolean): ColorScheme? {
	val primaryColor = parseHexColor(primary) ?: return null
	val secondaryColor = parseHexColor(secondary) ?: return null
	val scheme = dynamicColorScheme(
		seedColor = primaryColor,
		secondary = secondaryColor,
		tertiary = secondaryColor,
		isDark = isDark,
		isAmoled = false,
		style = PaletteStyle.Rainbow,
		modifyColorScheme = { base ->
			// surfaceTint is what M3 blends onto elevated surfaces (cards,
			// dialogs, top bars, bottom sheets). Default is `primary`, which
			// drowns out the secondary color choice. Re-pointing it to
			// `secondary` gives the user's accent visibility on every elevated
			// surface in the project — the most pervasive non-FAB lever.
			base.copy(surfaceTint = base.secondary)
		},
	)
	return scheme
}

internal fun Color.toArgbHex(): String {
	val a = (alpha * 255).toInt().coerceIn(0, 255)
	val r = (red * 255).toInt().coerceIn(0, 255)
	val g = (green * 255).toInt().coerceIn(0, 255)
	val b = (blue * 255).toInt().coerceIn(0, 255)
	return "#" + listOf(a, r, g, b).joinToString("") { it.toString(16).padStart(2, '0').uppercase() }
}

@Composable
fun ProjectThemeOverride(
	theme: ProjectTheme?,
	content: @Composable () -> Unit,
) {
	if (theme == null) {
		content()
		return
	}

	val parentIsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
	val derived = remember(theme, parentIsDark) { theme.toColorScheme(parentIsDark) }

	if (derived == null) {
		content()
	} else {
		MaterialTheme(
			colorScheme = derived,
			typography = MaterialTheme.typography,
			shapes = MaterialTheme.shapes,
			content = content,
		)
	}
}

internal fun parseHexColor(hex: String): Color? {
	val cleaned = hex.removePrefix("#")
	val (a, r, g, b) = when (cleaned.length) {
		6 -> arrayOf("FF", cleaned.substring(0, 2), cleaned.substring(2, 4), cleaned.substring(4, 6))
		8 -> arrayOf(cleaned.substring(0, 2), cleaned.substring(2, 4), cleaned.substring(4, 6), cleaned.substring(6, 8))
		else -> return null
	}
	return runCatching {
		Color(
			alpha = a.toInt(16) / 255f,
			red = r.toInt(16) / 255f,
			green = g.toInt(16) / 255f,
			blue = b.toInt(16) / 255f,
		)
	}.getOrNull()
}
