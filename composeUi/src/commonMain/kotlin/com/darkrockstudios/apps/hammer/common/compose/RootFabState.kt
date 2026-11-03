package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Shared by the floating action button the project scaffold draws and the screen beside it: the
 * screen sets [scrolledDown] while its content is scrolled down, and the button stays out of the way
 * until it is cleared. A screen that sets it clears it when it leaves.
 */
@Stable
class RootFabState {
	var scrolledDown by mutableStateOf(false)
}

@Composable
fun rememberRootFabState(): RootFabState = remember { RootFabState() }
