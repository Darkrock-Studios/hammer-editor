package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.staticCompositionLocalOf
import com.darkrockstudios.apps.hammer.common.uiNeedsExplicitCloseButtons

data class ScreenCharacteristics(
	val windowWidthClass: WindowWidthSizeClass,
	val windowHeightClass: WindowHeightSizeClass,
	val needsExplicitClose: Boolean
)

val LocalScreenCharacteristic = staticCompositionLocalOf {
	ScreenCharacteristics(
		windowWidthClass = WindowWidthSizeClass.Compact,
		windowHeightClass = WindowHeightSizeClass.Compact,
		needsExplicitClose = uiNeedsExplicitCloseButtons()
	)
}