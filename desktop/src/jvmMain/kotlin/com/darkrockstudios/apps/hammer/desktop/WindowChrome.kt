package com.darkrockstudios.apps.hammer.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.LocalWindowTitleBarHeight
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.material.MaterialTitleBar

/**
 * Draws the window's title bar showing [title], then [content] with [LocalWindowTitleBarHeight]
 * set to the bar's measured height.
 */
@Composable
internal fun DecoratedWindowScope.TitledWindowContent(
	title: String,
	content: @Composable () -> Unit,
) {
	val density = LocalDensity.current
	var titleBarHeight by remember { mutableStateOf(0.dp) }
	MaterialTitleBar(
		modifier = Modifier.onSizeChanged { titleBarHeight = with(density) { it.height.toDp() } },
	) {
		Text(
			text = title,
			color = MaterialTheme.colorScheme.onSurface,
			modifier = Modifier.align(Alignment.CenterHorizontally),
		)
	}

	CompositionLocalProvider(LocalWindowTitleBarHeight provides titleBarHeight, content = content)
}
