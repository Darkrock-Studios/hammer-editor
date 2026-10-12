package com.darkrockstudios.apps.hammer.common.compose.markdowneditor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** An action's tint: Material's disabled content colour, else the active or the resting one. */
@Composable
internal fun editorActionColor(active: Boolean, enabled: Boolean): Color = when {
	!enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
	active -> MaterialTheme.colorScheme.inversePrimary
	else -> MaterialTheme.colorScheme.onSurfaceVariant
}
