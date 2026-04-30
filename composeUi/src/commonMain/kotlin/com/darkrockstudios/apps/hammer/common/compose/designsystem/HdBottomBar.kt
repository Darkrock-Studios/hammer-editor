package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val PillShape = RoundedCornerShape(16.dp)

/**
 * Slim icon-only bottom bar — the phone counterpart to [HdNavRail].
 *
 * M3's [androidx.compose.material3.NavigationBar] is 80dp + gesture inset,
 * which is heavy for a writing app. This sits at [contentHeight] (default
 * 56dp) above the gesture inset.
 */
@Composable
fun HdBottomBar(
	modifier: Modifier = Modifier,
	contentHeight: Dp = 56.dp,
	content: @Composable RowScope.() -> Unit,
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface)
			.windowInsetsPadding(WindowInsets.navigationBars)
			.height(contentHeight),
		horizontalArrangement = Arrangement.SpaceEvenly,
		verticalAlignment = Alignment.CenterVertically,
		content = content,
	)
}

/**
 * One cell of [HdBottomBar]. The cell takes the full vertical space so
 * taps land reliably outside the pill, while the pill stays centered.
 */
@Composable
fun RowScope.HdBottomBarItem(
	selected: Boolean,
	onClick: () -> Unit,
	icon: @Composable () -> Unit,
	label: String,
) {
	val pillColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
	val iconColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
	Box(
		modifier = Modifier
			.weight(1f)
			.fillMaxHeight()
			.clickable(onClick = onClick)
			.semantics { contentDescription = label },
		contentAlignment = Alignment.Center,
	) {
		Box(
			modifier = Modifier
				.clip(PillShape)
				.background(pillColor)
				.padding(horizontal = 16.dp, vertical = 4.dp),
			contentAlignment = Alignment.Center,
		) {
			CompositionLocalProvider(LocalContentColor provides iconColor) {
				icon()
			}
		}
	}
}
