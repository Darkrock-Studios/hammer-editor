package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
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

/**
 * Size classes come from the constraints of the space the UI occupies rather than from
 * `calculateWindowSizeClass()`: the desktop implementation of that reads an AWT window,
 * which does not exist under the Tao window backend.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberWindowSizeClass(constraints: Constraints): WindowSizeClass {
	val density = LocalDensity.current
	return remember(constraints, density) {
		WindowSizeClass.calculateFromSize(
			size = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
			density = density,
		)
	}
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