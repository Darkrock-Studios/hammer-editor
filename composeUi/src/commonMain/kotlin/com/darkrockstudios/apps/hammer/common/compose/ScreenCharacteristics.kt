package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
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

@Composable
fun SetScreenCharacteristics(wideThreshold: Dp, content: @Composable BoxWithConstraintsScope.() -> Unit) {
	BoxWithConstraints {
		val isWide by remember(maxWidth) { derivedStateOf { maxWidth >= wideThreshold } }
		val windowSizeClass = rememberWindowSizeClass(constraints)

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