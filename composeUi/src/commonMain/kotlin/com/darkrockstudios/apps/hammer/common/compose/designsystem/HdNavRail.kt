package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Vertical navigation rail. Use [HdNavRailItem] for items so the
 * project's secondary tint drives the selected pill.
 */
@Composable
fun HdNavRail(
	modifier: Modifier = Modifier,
	header: @Composable (ColumnScope.() -> Unit)? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	NavigationRail(
		modifier = modifier.padding(top = 8.dp),
		header = header,
		content = content,
	)
}

@Composable
fun HdNavRailItem(
	selected: Boolean,
	onClick: () -> Unit,
	icon: @Composable () -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	enabled: Boolean = true,
	colors: NavigationRailItemColors = hdNavRailItemColors(),
) {
	NavigationRailItem(
		selected = selected,
		onClick = onClick,
		icon = icon,
		label = { Text(label) },
		modifier = modifier,
		enabled = enabled,
		alwaysShowLabel = true,
		colors = colors,
	)
}

/**
 * Default item colors. Selected pill pulls from `secondaryContainer` so
 * the per-project theme override drives the accent (rather than primary).
 */
@Composable
fun hdNavRailItemColors(): NavigationRailItemColors = NavigationRailItemDefaults.colors(
	selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
	selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
	indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
	unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
	unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
