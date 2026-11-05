package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Shared by the floating action button the project scaffold draws and the screen beside it: the
 * screen sets [scrolledDown] while its content is scrolled down, and the button stays out of the way
 * until it is cleared. A screen that sets it clears it when it leaves.
 */
@Stable
class RootFabState {
	var scrolledDown by mutableStateOf(false)
}

/** Null outside a project scaffold: the screen has no button to move and the button stays put. */
val LocalRootFabState = staticCompositionLocalOf<RootFabState?> { null }

/** Gives [content] one [RootFabState] for the button and the screen it floats over to share. */
@Composable
fun ProvideRootFabState(content: @Composable () -> Unit) {
	val state = remember { RootFabState() }
	CompositionLocalProvider(LocalRootFabState provides state, content = content)
}
