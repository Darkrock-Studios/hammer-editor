package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.nav_rail_collapse
import com.darkrockstudios.apps.hammer.nav_rail_expand
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

data class HdNavRailDestination<T>(
	val id: T,
	val icon: ImageVector,
	val label: String,
	val shortLabel: String,
)

private val CollapsedWidth = 72.dp
private val ExpandedWidth = 176.dp
private val ItemHeight = 72.dp
private val IndicatorWidth = 3.dp
private val IndicatorInset = 12.dp
private val AnimSpec = tween<Dp>(durationMillis = 240, easing = FastOutSlowInEasing)

/**
 * Vertical navigation rail with two states. Collapsed shows the icon
 * stacked over a short mono caption; expanded slides the rail wider
 * and shows the full label beside the icon. A `secondary` hairline
 * indicator on the left edge slides between destinations when
 * selection changes — the rail's chrome stays grayscale otherwise.
 *
 *     ┌────┐    ┌──────────────────┐
 *     │ ▌☉ │    │ ▌ ☉  Home        │
 *     │HOME│    │   ◇  Story       │
 *     │ ◇  │    │   ✦  Notes       │
 *     │STRY│    │   ⚑  Encyclopedia│
 *     │ ✦  │    │   ✶  Time Line   │
 *     │NOTE│    │                  │
 *     │ ⚑  │    │ ──────────────── │
 *     │ENCY│    │   ‹              │
 *     │ ✶  │    │  v2.2.0          │
 *     │TIME│    └──────────────────┘
 *     │    │
 *     │ ── │
 *     │ ›  │
 *     │v.. │
 *     └────┘
 */
@Composable
fun <T> HdNavRail(
	destinations: List<HdNavRailDestination<T>>,
	selectedId: T,
	onSelect: (T) -> Unit,
	expanded: Boolean,
	onToggleExpanded: () -> Unit,
	modifier: Modifier = Modifier,
	footer: @Composable (ColumnScope.() -> Unit)? = null,
	itemTestTag: ((T) -> String)? = null,
) {
	val width by animateDpAsState(
		targetValue = if (expanded) ExpandedWidth else CollapsedWidth,
		animationSpec = AnimSpec,
	)
	val selectedIndex = destinations.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
	val indicatorY by animateDpAsState(
		targetValue = ItemHeight * selectedIndex + IndicatorInset,
		animationSpec = AnimSpec,
	)
	val borderColor = MaterialTheme.colorScheme.outlineVariant

	Column(
		modifier = modifier
			.fillMaxHeight()
			.width(width)
			.background(MaterialTheme.colorScheme.surface)
			.drawBehind {
				val strokeWidth = Dp.Hairline.toPx().coerceAtLeast(1f)
				val x = if (layoutDirection == LayoutDirection.Ltr) size.width else 0f
				drawLine(
					color = borderColor,
					start = Offset(x, 0f),
					end = Offset(x, size.height),
					strokeWidth = strokeWidth,
				)
			}
			.clipToBounds()
			.padding(top = 8.dp),
	) {
		Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
			Column(modifier = Modifier.fillMaxWidth()) {
				destinations.forEach { destination ->
					HdNavRailRow(
						destination = destination,
						selected = destination.id == selectedId,
						expanded = expanded,
						onClick = { onSelect(destination.id) },
						testTag = itemTestTag?.invoke(destination.id),
					)
				}
			}

			Box(
				modifier = Modifier
					.offset { IntOffset(0, indicatorY.roundToPx()) }
					.width(IndicatorWidth)
					.height(ItemHeight - IndicatorInset * 2)
					.background(MaterialTheme.colorScheme.secondary),
			)
		}

		if (footer != null) {
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)
		}
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(vertical = 8.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(4.dp),
		) {
			HdNavRailToggle(expanded = expanded, onToggle = onToggleExpanded)
			if (footer != null) footer()
		}
	}
}

@Composable
private fun <T> HdNavRailRow(
	destination: HdNavRailDestination<T>,
	selected: Boolean,
	expanded: Boolean,
	onClick: () -> Unit,
	testTag: String? = null,
) {
	val iconColor = if (selected) MaterialTheme.colorScheme.secondary
	else MaterialTheme.colorScheme.onSurfaceVariant
	val textColor = if (selected) MaterialTheme.colorScheme.onSurface
	else MaterialTheme.colorScheme.onSurfaceVariant

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(ItemHeight)
			.clickable(onClick = onClick)
			.semantics { contentDescription = destination.label }
			.then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
		verticalAlignment = Alignment.CenterVertically,
	) {
		AnimatedContent(
			targetState = expanded,
			transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
			label = "navRailItem",
		) { isExpanded ->
			if (isExpanded) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(16.dp),
				) {
					Icon(
						imageVector = destination.icon,
						contentDescription = null,
						tint = iconColor,
						modifier = Modifier.size(24.dp),
					)
					HdMonoLabel(
						text = destination.label,
						color = textColor,
						style = MaterialTheme.typography.labelMedium,
						maxLines = 1,
						softWrap = false,
						overflow = TextOverflow.Ellipsis,
					)
				}
			} else {
				Column(
					modifier = Modifier.fillMaxWidth(),
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
		}
	}
}

@Composable
private fun HdNavRailToggle(
	expanded: Boolean,
	onToggle: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val rotation by animateFloatAsState(
		targetValue = if (expanded) 0f else 180f,
		animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
	)
	val toggleLabel = (if (expanded) Res.string.nav_rail_collapse else Res.string.nav_rail_expand).get()
	IconButton(
		onClick = onToggle,
		modifier = modifier.semantics { contentDescription = toggleLabel },
	) {
		Icon(
			imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
			contentDescription = null,
			modifier = Modifier.graphicsLayer { rotationZ = rotation },
			tint = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
