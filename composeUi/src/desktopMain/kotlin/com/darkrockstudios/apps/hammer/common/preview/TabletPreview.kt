package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.darkrockstudios.apps.hammer.common.compose.LocalScreenCharacteristic
import com.darkrockstudios.apps.hammer.common.compose.ScreenCharacteristics
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.uiNeedsExplicitCloseButtons

/**
 * Landscape tablet canvas, applied via `@Preview(widthDp = TABLET_WIDTH_DP, ...)`.
 * The render canvas comes from the annotation, not a child modifier, so the
 * dimensions must be set there.
 */
const val TABLET_WIDTH_DP: Int = 1280
const val TABLET_HEIGHT_DP: Int = 800

/**
 * Wraps [content] in the app theme and a wide [ScreenCharacteristics] so screens
 * that branch on `LocalScreenCharacteristic` render their expanded/wide layouts
 * instead of the compact phone path. Pair with `@Preview(widthDp = TABLET_WIDTH_DP,
 * heightDp = TABLET_HEIGHT_DP)` to size the canvas.
 */
@Composable
fun TabletPreviewSurface(
	content: @Composable () -> Unit,
) {
	AppTheme(globalSettingsPreview) {
		CompositionLocalProvider(
			LocalScreenCharacteristic provides ScreenCharacteristics(
				isWide = true,
				windowWidthClass = WindowWidthSizeClass.Expanded,
				windowHeightClass = WindowHeightSizeClass.Medium,
				needsExplicitClose = uiNeedsExplicitCloseButtons(),
			),
		) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.background),
			) {
				content()
			}
		}
	}
}
