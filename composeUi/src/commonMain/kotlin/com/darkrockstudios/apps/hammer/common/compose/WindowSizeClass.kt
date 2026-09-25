package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width buckets, using the Material 3 window size class breakpoints. */
enum class WindowWidthSizeClass { Compact, Medium, Expanded }

/** Height buckets, using the Material 3 window size class breakpoints. */
enum class WindowHeightSizeClass { Compact, Medium, Expanded }

data class WindowSizeClass(
	val widthSizeClass: WindowWidthSizeClass,
	val heightSizeClass: WindowHeightSizeClass,
) {
	companion object {
		private val MediumWidth = 600.dp
		private val ExpandedWidth = 840.dp
		private val MediumHeight = 480.dp
		private val ExpandedHeight = 900.dp

		fun widthClassOf(width: Dp): WindowWidthSizeClass = when {
			width < MediumWidth -> WindowWidthSizeClass.Compact
			width < ExpandedWidth -> WindowWidthSizeClass.Medium
			else -> WindowWidthSizeClass.Expanded
		}

		fun heightClassOf(height: Dp): WindowHeightSizeClass = when {
			height < MediumHeight -> WindowHeightSizeClass.Compact
			height < ExpandedHeight -> WindowHeightSizeClass.Medium
			else -> WindowHeightSizeClass.Expanded
		}

		fun calculateFromSize(widthPx: Int, heightPx: Int, density: Density): WindowSizeClass =
			with(density) {
				WindowSizeClass(
					widthSizeClass = widthClassOf(widthPx.toDp()),
					heightSizeClass = heightClassOf(heightPx.toDp()),
				)
			}
	}
}

/** Derived from the space the UI occupies; the Tao backend has no AWT window to measure. */
@Composable
fun rememberWindowSizeClass(constraints: Constraints): WindowSizeClass {
	val density = LocalDensity.current
	return remember(constraints, density) {
		WindowSizeClass.calculateFromSize(constraints.maxWidth, constraints.maxHeight, density)
	}
}
