package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import dev.nucleusframework.application.NucleusDecoratedWindowScope
import java.awt.Toolkit

/** Brings this window to the front whenever [requests] goes up, including when it first shows with some made. */
@Composable
internal fun NucleusDecoratedWindowScope.RaiseOnRequest(requests: Int) {
	LaunchedEffect(requests) {
		if (requests == 0) return@LaunchedEffect
		nucleusWindow.setMinimized(false)
		nucleusWindow.toFront()
		nucleusWindow.requestFocus()
	}
}

fun coerceWindowSize(targetWidth: Dp, targetHeight: Dp): DpSize {
	val min = 100.dp
	val maxPercent = 0.9
	val screenSize = Toolkit.getDefaultToolkit().screenSize
	return DpSize(
		width = targetWidth.coerceIn(min, (screenSize.width * maxPercent).dp),
		height = targetHeight.coerceIn(min, (screenSize.height * maxPercent).dp),
	)
}
