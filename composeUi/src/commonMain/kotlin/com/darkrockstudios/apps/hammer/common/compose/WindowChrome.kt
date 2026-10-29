package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The height of the window chrome drawn inside the window at its top: the desktop title bar with
 * its controls, which a dialog layer would otherwise cover. Zero where the platform draws the
 * chrome outside the window's content.
 */
val LocalWindowTitleBarHeight: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }

/** Keeps the content below the window's title bar, where a dialog layer spans the whole window. */
@Composable
fun Modifier.belowWindowChrome(): Modifier = padding(top = LocalWindowTitleBarHeight.current)
