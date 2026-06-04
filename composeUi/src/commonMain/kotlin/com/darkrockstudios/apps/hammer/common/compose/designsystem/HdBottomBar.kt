package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

data class HdBottomBarDestination<T>(
	val id: T,
	val icon: ImageVector,
	val label: String,
	val shortLabel: String,
)

private val BarHeight = 64.dp
private val IndicatorHeight = 3.dp
private val IndicatorAnimSpec = tween<Int>(durationMillis = 240, easing = FastOutSlowInEasing)

/**
 * Slim icon-plus-mono-caption bottom bar — the phone counterpart to
 * [HdNavRail]. Mirrors the rail's vocabulary: hairline top rule, square
 * corners, no pill background, and a `secondary` indicator that slides
 * along the **top** edge between cells when selection changes (the rail
 * uses the left edge).
 *
 *     ─────────────────────────────────────
 *      ▔▔▔
 *       ☉    ◇    ✦    ⚑    ✶
 *      HOME STRY NOTE ENCY TIME
 *
 * M3's [androidx.compose.material3.NavigationBar] is 80dp + gesture
 * inset, which is heavy for a writing app. This sits at [contentHeight]
 * (default 64dp) above the gesture inset.
 */
@Composable
fun <T> HdBottomBar(
	destinations: List<HdBottomBarDestination<T>>,
	selectedId: T,
	onSelect: (T) -> Unit,
	modifier: Modifier = Modifier,
	contentHeight: Dp = BarHeight,
	itemTestTag: ((T) -> String)? = null,
) {
	val selectedIndex = destinations.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
	var barWidthPx by remember { mutableIntStateOf(0) }
	val itemWidthPx = if (destinations.isEmpty()) 0 else barWidthPx / destinations.size
	val indicatorX by animateIntAsState(
		targetValue = itemWidthPx * selectedIndex,
		animationSpec = IndicatorAnimSpec,
		label = "indicatorX",
	)

	Column(
		modifier = modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface)
			.windowInsetsPadding(WindowInsets.navigationBars),
	) {
		HorizontalDivider(
			thickness = Dp.Hairline,
			color = MaterialTheme.colorScheme.outlineVariant,
		)
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.height(contentHeight)
				.onSizeChanged { barWidthPx = it.width },
		) {
			Row(
				modifier = Modifier.fillMaxSize(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				destinations.forEach { destination ->
					HdBottomBarItem(
						destination = destination,
						selected = destination.id == selectedId,
						onClick = { onSelect(destination.id) },
						testTag = itemTestTag?.invoke(destination.id),
					)
				}
			}

			if (itemWidthPx > 0) {
				val itemWidthDp = with(LocalDensity.current) { itemWidthPx.toDp() }
				Box(
					modifier = Modifier
						.offset { IntOffset(indicatorX, 0) }
						.width(itemWidthDp)
						.height(IndicatorHeight)
						.background(MaterialTheme.colorScheme.secondary),
				)
			}
		}
	}
}

@Composable
private fun <T> RowScope.HdBottomBarItem(
	destination: HdBottomBarDestination<T>,
	selected: Boolean,
	onClick: () -> Unit,
	testTag: String? = null,
) {
	val iconColor = if (selected) MaterialTheme.colorScheme.secondary
	else MaterialTheme.colorScheme.onSurfaceVariant
	val textColor = if (selected) MaterialTheme.colorScheme.onSurface
	else MaterialTheme.colorScheme.onSurfaceVariant

	Column(
		modifier = Modifier
			.weight(1f)
			.fillMaxHeight()
			.clickable(onClick = onClick)
			.semantics { contentDescription = destination.label }
			.then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		Icon(
			imageVector = destination.icon,
			contentDescription = null,
			tint = iconColor,
			modifier = Modifier.size(24.dp),
		)
		Spacer(modifier = Modifier.height(4.dp))
		HdMonoLabel(
			text = destination.shortLabel,
			color = textColor,
			maxLines = 1,
		)
	}
}
