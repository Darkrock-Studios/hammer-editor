package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.SetScreenCharacteristics
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme

/**
 * Landscape tablet canvas, applied via `@Preview(widthDp = TABLET_WIDTH_DP, ...)`.
 * The render canvas comes from the annotation, not a child modifier, so the
 * dimensions must be set there.
 */
const val TABLET_WIDTH_DP: Int = 1280
const val TABLET_HEIGHT_DP: Int = 800

/**
 * Extra-tall canvas for screens whose content scrolls well past a normal tablet
 * height (e.g. project stats, encyclopedia entries). Use it as the `heightDp` so
 * the whole screen renders for translation screenshots instead of being clipped.
 */
const val TABLET_TALL_HEIGHT_DP: Int = 1400

/** Matches ProjectRootUi's threshold so previews pick the same layout the app would. */
private val PREVIEW_WIDE_THRESHOLD = 650.dp

/**
 * Wraps [content] in the app theme and derives `LocalScreenCharacteristic` from the
 * canvas size, as the app does. On a `TABLET_WIDTH_DP` canvas screens take their
 * expanded/wide layouts; the screen gallery reuses these previews on phone-sized
 * canvases to get the compact ones. Light/dark follows `isSystemInDarkTheme()`.
 */
@Composable
fun TabletPreviewSurface(
	content: @Composable () -> Unit,
) {
	AppTheme(globalSettingsPreview) {
		SetScreenCharacteristics(PREVIEW_WIDE_THRESHOLD) {
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
