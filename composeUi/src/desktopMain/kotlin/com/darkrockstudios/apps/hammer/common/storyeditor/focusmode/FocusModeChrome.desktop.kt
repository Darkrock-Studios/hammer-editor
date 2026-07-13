package com.darkrockstudios.apps.hammer.common.storyeditor.focusmode

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Matches the nucleus decorated-window default title bar height, which the OS reserves for the
// native window controls at the top of the window.
private val TitleBarHeight = 40.dp

actual fun Modifier.focusModeChromePadding(): Modifier = padding(top = TitleBarHeight)
