package com.darkrockstudios.apps.hammer.common.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.ProvideMarkdownConfig
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettings


val LightColors = lightColorScheme(
	primary = md_theme_light_primary,
	onPrimary = md_theme_light_onPrimary,
	primaryContainer = md_theme_light_primaryContainer,
	onPrimaryContainer = md_theme_light_onPrimaryContainer,
	secondary = md_theme_light_secondary,
	onSecondary = md_theme_light_onSecondary,
	secondaryContainer = md_theme_light_secondaryContainer,
	onSecondaryContainer = md_theme_light_onSecondaryContainer,
	tertiary = md_theme_light_tertiary,
	onTertiary = md_theme_light_onTertiary,
	tertiaryContainer = md_theme_light_tertiaryContainer,
	onTertiaryContainer = md_theme_light_onTertiaryContainer,
	error = md_theme_light_error,
	errorContainer = md_theme_light_errorContainer,
	onError = md_theme_light_onError,
	onErrorContainer = md_theme_light_onErrorContainer,
	background = md_theme_light_background,
	onBackground = md_theme_light_onBackground,
	surface = md_theme_light_surface,
	onSurface = md_theme_light_onSurface,
	surfaceVariant = md_theme_light_surfaceVariant,
	onSurfaceVariant = md_theme_light_onSurfaceVariant,
	outline = md_theme_light_outline,
	outlineVariant = md_theme_light_outlineVariant,
	inverseOnSurface = md_theme_light_inverseOnSurface,
	inverseSurface = md_theme_light_inverseSurface,
	inversePrimary = md_theme_light_inversePrimary,
	surfaceTint = md_theme_light_surfaceTint,
	scrim = md_theme_light_scrim,
)


val DarkColors = darkColorScheme(
	primary = md_theme_dark_primary,
	onPrimary = md_theme_dark_onPrimary,
	primaryContainer = md_theme_dark_primaryContainer,
	onPrimaryContainer = md_theme_dark_onPrimaryContainer,
	secondary = md_theme_dark_secondary,
	onSecondary = md_theme_dark_onSecondary,
	secondaryContainer = md_theme_dark_secondaryContainer,
	onSecondaryContainer = md_theme_dark_onSecondaryContainer,
	tertiary = md_theme_dark_tertiary,
	onTertiary = md_theme_dark_onTertiary,
	tertiaryContainer = md_theme_dark_tertiaryContainer,
	onTertiaryContainer = md_theme_dark_onTertiaryContainer,
	error = md_theme_dark_error,
	errorContainer = md_theme_dark_errorContainer,
	onError = md_theme_dark_onError,
	onErrorContainer = md_theme_dark_onErrorContainer,
	background = md_theme_dark_background,
	onBackground = md_theme_dark_onBackground,
	surface = md_theme_dark_surface,
	onSurface = md_theme_dark_onSurface,
	surfaceVariant = md_theme_dark_surfaceVariant,
	onSurfaceVariant = md_theme_dark_onSurfaceVariant,
	outline = md_theme_dark_outline,
	outlineVariant = md_theme_dark_outlineVariant,
	inverseOnSurface = md_theme_dark_inverseOnSurface,
	inverseSurface = md_theme_dark_inverseSurface,
	inversePrimary = md_theme_dark_inversePrimary,
	surfaceTint = md_theme_dark_surfaceTint,
	scrim = md_theme_dark_scrim,
)

fun resolveColorScheme(useDarkTheme: Boolean): ColorScheme {
	return if (!useDarkTheme) {
		LightColors
	} else {
		DarkColors
	}
}

// Manuscript shape system — every M3 component reads square corners by
// default. Per-component rounding (e.g. chart bars) is opted in
// explicitly. See designsystem/README.md.
private val SquareCorners = RoundedCornerShape(0.dp)
private val HammerShapes = Shapes(
	extraSmall = SquareCorners,
	small = SquareCorners,
	medium = SquareCorners,
	large = SquareCorners,
	extraLarge = SquareCorners,
)

@Composable
fun AppTheme(
	settings: GlobalSettings,
	useDarkTheme: Boolean = isSystemInDarkTheme(),
	getOverrideColorScheme: ((Boolean) -> ColorScheme?)? = null,
	content: @Composable () -> Unit
) {
	val colors = remember(useDarkTheme) {
		getOverrideColorScheme?.invoke(useDarkTheme) ?: resolveColorScheme(useDarkTheme)
	}

	val extendedColors = if (useDarkTheme) DarkHammerColors else LightHammerColors

	CompositionLocalProvider(LocalHammerColors provides extendedColors) {
		MaterialTheme(
			colorScheme = colors,
			shapes = HammerShapes,
			typography = HammerTypography,
		) {
			ProvideMarkdownConfig(isDark = useDarkTheme, settings = settings) {
				content()
			}
		}
	}
}