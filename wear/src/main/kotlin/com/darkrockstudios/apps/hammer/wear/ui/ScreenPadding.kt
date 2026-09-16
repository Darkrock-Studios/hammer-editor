package com.darkrockstudios.apps.hammer.wear.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** Wear's own horizontal content inset, whose `PaddingDefaults` is internal to the library. */
private const val HORIZONTAL_PADDING_FRACTION = 0.052f

/**
 * The scaffold's vertical padding plus the side inset a round display needs. Without it, centered
 * text near the top and bottom of the screen runs under the bezel and loses its first and last
 * characters.
 */
@Composable
fun screenContentPadding(scaffoldPadding: PaddingValues): PaddingValues {
	val horizontal = LocalConfiguration.current.screenWidthDp.dp * HORIZONTAL_PADDING_FRACTION
	return PaddingValues(
		start = horizontal,
		end = horizontal,
		top = scaffoldPadding.calculateTopPadding(),
		bottom = scaffoldPadding.calculateBottomPadding(),
	)
}
