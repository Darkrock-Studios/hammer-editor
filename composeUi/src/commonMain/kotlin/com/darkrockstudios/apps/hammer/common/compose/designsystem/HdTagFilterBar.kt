package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.tagindex.TagCount
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The tag-count filter row of a browse screen: an "all" cell, a hairline separator, then one
 * [HdTagChip] per tag (`label · count`) in a horizontally scrolling row. Optional [leading]
 * slot (e.g. an inline search field on Expanded widths) and [trailing] slot (e.g. a sort menu),
 * both pinned. [scrollingLeading] opens the scrolling row behind its own hairline, for a second
 * set of filter chips that may outgrow the row. A mouse scrolls the row with its wheel or by dragging.
 *
 *     [⌕ search] │ ALL · 12 │ # gothic · 3  # coastal · 1 …   [SORT ▾]
 *     ALL · 12 │ ▪ Alice · 4  ▪ Queen · 2 │ # gothic · 3 …     (scrollingLeading)
 *
 * The row always renders even with no tags, so the trailing affordance has a stable home.
 * [allLabel] arrives pre-formatted (`"ALL · 12"`) so the component stays string-resource free.
 */
@Composable
fun HdTagFilterBar(
	tags: List<TagCount>,
	allLabel: String,
	activeTags: Set<String>,
	onToggle: (String) -> Unit,
	onClear: () -> Unit,
	modifier: Modifier = Modifier,
	leading: (@Composable () -> Unit)? = null,
	scrollingLeading: (@Composable () -> Unit)? = null,
	trailing: @Composable () -> Unit = {},
) {
	val allActive = activeTags.isEmpty()
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = Ui.Padding.XL, vertical = Ui.Padding.S),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
	) {
		if (leading != null) {
			leading()
			VerticalHairline()
		}
		AllChip(
			label = allLabel,
			active = allActive,
			onClick = onClear,
		)
		VerticalHairline()
		val rowState = rememberLazyListState()
		LazyRow(
			state = rowState,
			modifier = Modifier
				.weight(1f)
				.mouseScrollsSideways(rowState),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (scrollingLeading != null) {
				item(key = SCROLLING_LEADING_KEY) { scrollingLeading() }
				if (tags.isNotEmpty()) {
					item(key = SCROLLING_LEADING_RULE_KEY) {
						VerticalHairline(modifier = Modifier.padding(horizontal = Ui.Padding.M - 6.dp))
					}
				}
			}
			items(count = tags.size) { i ->
				val (label, count) = tags[i]
				val isActive = label in activeTags
				HdTagChip(
					label = "$label · $count",
					active = isActive,
					onClick = { onToggle(label) },
				)
			}
		}
		trailing()
	}
}

private const val SCROLLING_LEADING_KEY = "scrolling-leading"
private const val SCROLLING_LEADING_RULE_KEY = "scrolling-leading-rule"

/**
 * Lets a mouse reach chips past the edge: the vertical wheel scrolls the row sideways, and a drag pans
 * it. Touch and horizontal wheels already scroll a LazyRow.
 */
private fun Modifier.mouseScrollsSideways(state: LazyListState): Modifier = pointerInput(state) {
	val wheelStep = WHEEL_STEP.toPx()
	coroutineScope {
		launch {
			awaitPointerEventScope {
				while (true) {
					val event = awaitPointerEvent()
					if (event.type != PointerEventType.Scroll || event.keyboardModifiers.isShiftPressed) continue
					val change = event.changes.firstOrNull() ?: continue
					val delta = change.scrollDelta
					if (delta.y == 0f || abs(delta.x) >= abs(delta.y)) continue
					if (state.dispatchRawDelta(delta.y * wheelStep) != 0f) change.consume()
				}
			}
		}
		launch {
			awaitEachGesture {
				val down = awaitFirstDown(requireUnconsumed = false)
				if (down.type != PointerType.Mouse) return@awaitEachGesture
				val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
					?: return@awaitEachGesture
				state.dispatchRawDelta(down.position.x - start.position.x)
				horizontalDrag(start.id) { change ->
					state.dispatchRawDelta(-change.positionChange().x)
					change.consume()
				}
			}
		}
	}
}

private val WHEEL_STEP = 48.dp

@Composable
private fun VerticalHairline(modifier: Modifier = Modifier) {
	Box(
		modifier = modifier
			.height(20.dp)
			.width(Dp.Hairline)
			.background(MaterialTheme.colorScheme.outlineVariant),
	)
}

@Composable
private fun AllChip(
	label: String,
	active: Boolean,
	onClick: () -> Unit,
) {
	val background = if (active) {
		MaterialTheme.colorScheme.surfaceContainerHigh
	} else {
		Color.Transparent
	}
	val labelColor = if (active) {
		MaterialTheme.colorScheme.onSurface
	} else {
		MaterialTheme.colorScheme.onSurfaceVariant
	}
	Box(
		modifier = Modifier
			.height(28.dp)
			.background(background, RectangleShape)
			.clickable(onClick = onClick)
			.padding(horizontal = Ui.Padding.L),
		contentAlignment = Alignment.Center,
	) {
		HdMonoLabel(text = label, color = labelColor, maxLines = 1, softWrap = false)
	}
}
