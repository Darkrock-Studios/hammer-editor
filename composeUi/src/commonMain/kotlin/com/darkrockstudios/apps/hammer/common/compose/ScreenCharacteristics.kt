package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import com.darkrockstudios.apps.hammer.common.uiNeedsExplicitCloseButtons

data class ScreenCharacteristics(
	val isWide: Boolean,
	val windowWidthClass: WindowWidthSizeClass,
	val windowHeightClass: WindowHeightSizeClass,
	val needsExplicitClose: Boolean
)

val LocalScreenCharacteristic = staticCompositionLocalOf {
	ScreenCharacteristics(
		isWide = false,
		windowWidthClass = WindowWidthSizeClass.Compact,
		windowHeightClass = WindowHeightSizeClass.Compact,
		needsExplicitClose = uiNeedsExplicitCloseButtons()
	)
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun SetScreenCharacteristics(wideThreshold: Dp, content: @Composable BoxWithConstraintsScope.() -> Unit) {
	BoxWithConstraints {
		val isWide by remember(maxWidth) { derivedStateOf { maxWidth >= wideThreshold } }
		val windowSizeClass = calculateWindowSizeClass()

		CompositionLocalProvider(
			LocalScreenCharacteristic provides ScreenCharacteristics(
				isWide,
				windowSizeClass.widthSizeClass,
				windowSizeClass.heightSizeClass,
				uiNeedsExplicitCloseButtons()
			)
		) {
			content()
		}
	}
}