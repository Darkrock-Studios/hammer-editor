package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import java.awt.Toolkit

fun getScreenWidth() = Toolkit.getDefaultToolkit().screenSize.width.dp
fun getScreenHeight() = Toolkit.getDefaultToolkit().screenSize.height.dp

fun coerceWindowSize(targetWidth: Dp, targetHeight: Dp): DpSize {
	val min = 100.dp
	val maxPercent = 0.9
	val screenSize = Toolkit.getDefaultToolkit().screenSize
	return DpSize(
		width = targetWidth.coerceIn(min, (screenSize.width * maxPercent).dp),
		height = targetHeight.coerceIn(min, (screenSize.height * maxPercent).dp),
	)
}

/**
 * Maybe we won't always need this, but for not, scaling on linux is some times
 * broken in some distributions, and the UI is way too small.
 */
fun linuxScalingFix() {
	if (System.getProperty("os.name").lowercase().contains("linux")) {
		// Auto-detect system scaling
		System.setProperty("sun.java2d.uiScale.enabled", "true")
	}
}