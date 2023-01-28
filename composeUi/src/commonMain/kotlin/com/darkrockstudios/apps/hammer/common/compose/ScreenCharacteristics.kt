package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.staticCompositionLocalOf
import com.darkrockstudios.apps.hammer.common.uiNeedsExplicitCloseButtons

data class ScreenCharacteristics(
	val isWide: Boolean,
	val needsExplicitClose: Boolean
)

val LocalScreenCharacteristic = staticCompositionLocalOf {
	ScreenCharacteristics(
		isWide = false,
		needsExplicitClose = uiNeedsExplicitCloseButtons()
	)
}