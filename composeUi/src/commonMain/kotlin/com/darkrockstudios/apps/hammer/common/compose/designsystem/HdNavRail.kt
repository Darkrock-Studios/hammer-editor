package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemColors
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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

/**
 * Icon-only rail item. [label] surfaces via hover/long-press tooltip and
 * is exposed for accessibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
	val tooltipState = rememberTooltipState()
	TooltipBox(
		positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
		tooltip = { PlainTooltip { Text(label) } },
		state = tooltipState,
	) {
		NavigationRailItem(
			selected = selected,
			onClick = onClick,
			icon = icon,
			label = null,
			modifier = modifier,
			enabled = enabled,
			alwaysShowLabel = false,
			colors = colors,
		)
	}
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
