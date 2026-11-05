package com.darkrockstudios.apps.hammer.common.timeline.drawing

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarGutter
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitRegion
import com.darkrockstudios.apps.hammer.common.timeline.layout.HitTarget

// The desktop scrollbar takes a gutter; touch platforms overlay a transient one. Hover tooltips
// follow the same split.
internal val hoverTooltips: Boolean get() = MpScrollBarGutter > 0.dp

/**
 * Places one child per region at the region's rect. Placing the child itself, rather than offsetting
 * it, keeps a tooltip's anchor on the region. Each [content] call must emit exactly one layout node.
 */
@Composable
internal fun HitLayer(
	regions: List<HitRegion>,
	modifier: Modifier = Modifier,
	content: @Composable (index: Int, region: HitRegion) -> Unit,
) {
	Layout(
		modifier = modifier,
		// Keyed so a box keeps its gesture state when a relayout reorders the regions.
		content = { regions.forEachIndexed { i, region -> key(region.target) { content(i, region) } } },
	) { measurables, constraints ->
		val placeables = measurables.mapIndexed { i, measurable ->
			val rect = regions[i].rect
			val width = rect.width.dp.roundToPx().coerceAtLeast(0)
			val height = rect.height.dp.roundToPx().coerceAtLeast(0)
			measurable.measure(Constraints.fixed(width, height))
		}
		layout(constraints.maxWidth, constraints.maxHeight) {
			placeables.forEachIndexed { i, placeable ->
				val rect = regions[i].rect
				placeable.placeRelative(rect.x.dp.roundToPx(), rect.y.dp.roundToPx())
			}
		}
	}
}

/** What a hit box shows on hover: its description as plain text, or an event's card. */
internal sealed interface HitTooltip {
	data object Description : HitTooltip
	data class Card(val content: @Composable (eventId: Int) -> Unit) : HitTooltip
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HitBox(
	region: HitRegion,
	source: (@Composable BoxScope.() -> Unit)?,
	description: String,
	tooltip: HitTooltip?,
	peeked: Boolean,
	onTap: (HitTarget) -> Unit,
	onPeek: (eventId: Int) -> Unit,
	onHover: ((eventId: Int, hovered: Boolean) -> Unit)? = null,
) {
	val eventId = region.target.eventId
	val currentOnHover by rememberUpdatedState(onHover)

	val hover = if (onHover != null && eventId != null) {
		Modifier.pointerInput(eventId) {
			awaitPointerEventScope {
				while (true) {
					when (awaitPointerEvent().type) {
						PointerEventType.Enter -> currentOnHover?.invoke(eventId, true)
						PointerEventType.Exit -> currentOnHover?.invoke(eventId, false)
					}
				}
			}
		}
	} else {
		Modifier
	}
	val target = Modifier
		.testTag(region.target.testTag())
		.then(hover)
		.semantics { contentDescription = description }
		.combinedClickable(
			role = Role.Button,
			onLongClick = eventId?.let { id -> { onPeek(id) } },
			onClick = { onTap(region.target) },
		)
	val tooltipContent: (@Composable TooltipScope.() -> Unit)? = when {
		tooltip is HitTooltip.Card && eventId != null -> {
			{
				RichTooltip(
					modifier = Modifier.border(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
					shape = RectangleShape,
					colors = TooltipDefaults.richTooltipColors(
						containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
						contentColor = MaterialTheme.colorScheme.onSurface,
					),
					shadowElevation = 0.dp,
				) {
					tooltip.content(eventId)
				}
			}
		}

		tooltip == HitTooltip.Description && description.isNotBlank() -> {
			{ PlainTooltip { Text(description) } }
		}

		else -> null
	}
	if (tooltipContent != null) {
		// Hover drives the tooltip where a pointer hovers; on touch it shows while a long press holds.
		val state = rememberTooltipState(isPersistent = !hoverTooltips)
		if (!hoverTooltips) {
			LaunchedEffect(peeked) {
				if (peeked) state.show() else state.dismiss()
			}
		}
		TooltipBox(
			positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
			tooltip = tooltipContent,
			state = state,
			modifier = Modifier.fillMaxSize(),
			enableUserInput = hoverTooltips,
		) {
			Box(modifier = Modifier.fillMaxSize().then(target)) { source?.invoke(this) }
		}
	} else {
		Box(modifier = Modifier.fillMaxSize().then(target)) { source?.invoke(this) }
	}
}
