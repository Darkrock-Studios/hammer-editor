package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

/**
 * True while the soft keyboard is on screen. Inherently mobile-only: desktop/web report a
 * zero IME inset, so callers gating on this never collapse chrome there.
 */
@Composable
fun isImeVisible(): Boolean {
	val density = LocalDensity.current
	return WindowInsets.ime.getBottom(density) > 0
}
