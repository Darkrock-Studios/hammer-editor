package com.darkrockstudios.apps.hammer.common.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "Manuscript ledger" type scale.
 *
 * - displayLarge / displayMedium drop to FontWeight.Light at large sizes for
 *   the dashboard's big numerals (`23,214`, `+4,128`).
 * - labelSmall / labelMedium switch to the bundled [hammerMonoFontFamily]
 *   (IBM Plex Mono) with extra letter spacing. Apply [String.uppercase] at
 *   the call site rather than baking it into the style — keeps
 *   `Text(text, style)` predictable for non-label uses.
 */
@Composable
fun hammerTypography(): Typography {
	val mono = hammerMonoFontFamily()
	return Typography().run {
		copy(
			displayLarge = displayLarge.copy(
				fontWeight = FontWeight.Light,
				fontSize = 64.sp,
				lineHeight = 72.sp,
				letterSpacing = (-0.5).sp,
			),
			displayMedium = displayMedium.copy(
				fontWeight = FontWeight.Light,
				fontSize = 48.sp,
				lineHeight = 56.sp,
				letterSpacing = (-0.25).sp,
			),
			labelSmall = labelSmall.copy(
				fontFamily = mono,
				letterSpacing = 1.5.sp,
			),
			labelMedium = labelMedium.copy(
				fontFamily = mono,
				letterSpacing = 1.5.sp,
			),
		)
	}
}
