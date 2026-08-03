package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.Ui

/**
 * Tracks the scroll-away state of a [HdScrollAwayFooter]: wire
 * [nestedScrollConnection] to the scrolling content and feed [height] into
 * that content's bottom `contentPadding` so its last row can clear the strip.
 */
@Stable
class HdScrollAwayFooterState internal constructor() {
	var isHiddenByScroll by mutableStateOf(false)
		private set

	/**
	 * Measured height of the footer strip. Retained while the footer is
	 * hidden, so the list's bottom padding never changes.
	 */
	var height by mutableStateOf(0.dp)
		internal set

	internal var heightPx = 0f

	private var scrolledDown = 0f

	/**
	 * Hides on scrolled-past distance rather than direction alone, and counts
	 * only what the list actually consumed. A list whose sole scroll room is
	 * this strip's own bottom padding can never reach the threshold, so the
	 * strip stays put instead of hiding and immediately re-pinning at the
	 * bottom.
	 */
	val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
		override fun onPostScroll(
			consumed: Offset,
			available: Offset,
			source: NestedScrollSource,
		): Offset {
			if (consumed.y < -1f) {
				scrolledDown -= consumed.y
				if (scrolledDown > heightPx) isHiddenByScroll = true
			} else if (consumed.y > 1f) {
				scrolledDown = 0f
				isHiddenByScroll = false
			}
			return Offset.Zero
		}
	}
}

@Composable
fun rememberHdScrollAwayFooterState(): HdScrollAwayFooterState = remember { HdScrollAwayFooterState() }

/**
 * Hairline action strip that floats over the bottom of a scrolling pane and
 * slides away once the reader has scrolled down past the strip's own height.
 *
 *     ┌───────────────────────┐
 *     │ list row              │
 *     │ list row              │
 *     ├───────────────────────┤
 *     │ [ ACTION ]         ＋ │
 *     └───────────────────────┘
 *
 * The strip is an **overlay**, never a sibling in a `Column`: hiding it must
 * not resize the list. A footer that takes layout space changes what the list
 * can scroll, which flips any visibility rule keyed off scroll position and
 * flickers the strip on and off.
 *
 * Host it in the same `Box` as the scrolling content, and pass
 * [HdScrollAwayFooterState.height] as that content's bottom `contentPadding`.
 * The host `Box` should `clipToBounds()` so the strip disappears at the pane
 * edge as it slides out.
 *
 * [visible] defaults to the scroll-away rule; widen it to pin the strip open
 * (e.g. `!listState.canScrollForward || !state.isHiddenByScroll` keeps it
 * reachable once the list bottoms out).
 */
@Composable
fun BoxScope.HdScrollAwayFooter(
	state: HdScrollAwayFooterState,
	modifier: Modifier = Modifier,
	visible: Boolean = !state.isHiddenByScroll,
	containerColor: Color = MaterialTheme.colorScheme.surface,
	dividerColor: Color = MaterialTheme.colorScheme.outlineVariant,
	contentPadding: PaddingValues = PaddingValues(Ui.Padding.M),
	content: @Composable RowScope.() -> Unit,
) {
	val density = LocalDensity.current
	AnimatedVisibility(
		visible = visible,
		modifier = modifier.align(Alignment.BottomCenter),
		enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
		exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.background(containerColor)
				.onSizeChanged { size ->
					if (size.height > 0) {
						state.height = with(density) { size.height.toDp() }
						state.heightPx = size.height.toFloat()
					}
				},
		) {
			HorizontalDivider(
				thickness = Dp.Hairline,
				color = dividerColor,
			)
			Row(
				modifier = Modifier.fillMaxWidth().padding(contentPadding),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
				content = content,
			)
		}
	}
}
