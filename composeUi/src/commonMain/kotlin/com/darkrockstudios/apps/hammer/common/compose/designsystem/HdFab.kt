package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.border
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hammer-flavored floating action button — square corners, primary fill,
 * onPrimary content, hairline outline, no elevation. Sits in the same
 * vocabulary as [HdTypeStamp] and [HdEntryFilterBar]: structure comes
 * from rules, not shadows.
 *
 *     ┌──────┐
 *     │  +   │
 *     └──────┘
 */
@Composable
fun HdFab(
	onClick: () -> Unit,
	icon: ImageVector,
	contentDescription: String?,
	modifier: Modifier = Modifier,
) {
	FloatingActionButton(
		onClick = onClick,
		modifier = modifier
			.border(
				width = Dp.Hairline,
				color = MaterialTheme.colorScheme.outline,
				shape = RectangleShape,
			),
		shape = RectangleShape,
		containerColor = MaterialTheme.colorScheme.primary,
		contentColor = MaterialTheme.colorScheme.onPrimary,
		elevation = FloatingActionButtonDefaults.elevation(
			defaultElevation = 0.dp,
			pressedElevation = 0.dp,
			focusedElevation = 0.dp,
			hoveredElevation = 0.dp,
		),
	) {
		Icon(imageVector = icon, contentDescription = contentDescription)
	}
}
