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