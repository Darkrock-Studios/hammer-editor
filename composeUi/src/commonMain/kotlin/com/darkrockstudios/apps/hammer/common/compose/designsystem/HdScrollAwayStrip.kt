package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val HD_SCROLL_AWAY_STRIP_TAG = "Hd Scroll Away Strip"

/**
 * Where an [HdScrollAwayStrip] sits: [offset] is how far above its resting place it has slid, from
 * zero (shown) to minus its [height] (hidden).
 */
@Stable
class HdScrollAwayStripState internal constructor(private val scope: CoroutineScope) {
	var height by mutableIntStateOf(0)
		internal set
	private var hidden by mutableStateOf(false)
	private val slide = Animatable(0f)

	val offset: Float get() = slide.value

	/** The part of the strip still showing, in pixels. */
	val visibleHeight: Float get() = (height + offset).coerceAtLeast(0f)

	/**
	 * Hides the strip as the content starts scrolling down and shows it as the content starts back
	 * up. Only scrolling the content actually did counts, so content with nothing to scroll leaves the
	 * strip alone. It consumes nothing.
	 */
	val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
		override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
			when {
				consumed.y < 0f -> slideAway(true)
				consumed.y > 0f -> slideAway(false)
			}
			return Offset.Zero
		}
	}

	/**
	 * Shows the strip whenever the content is at its top. Call it with the content's scroll position
	 * whenever that changes, including without a scroll gesture, as when the content shrinks under a
	 * filter or jumps to its top; otherwise the strip could stay hidden with nothing left to scroll it
	 * back.
	 */
	fun followScrollPosition(position: Int) {
		if (position == 0) slideAway(false)
	}

	private fun slideAway(hide: Boolean) {
		if (hide == hidden) return
		hidden = hide
		scope.launch { slide.animateTo(target(), SlideSpec) }
	}

	internal fun onHeightChanged(newHeight: Int) {
		if (newHeight == height) return
		height = newHeight
		scope.launch { slide.snapTo(target()) }
	}

	private fun target(): Float = if (hidden) -height.toFloat() else 0f
}

private val SlideSpec = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)

@Composable
fun rememberHdScrollAwayStripState(): HdScrollAwayStripState {
	val scope = rememberCoroutineScope()
	return remember { HdScrollAwayStripState(scope) }
}

/**
 * A browse screen's filter strip laid over the top of its scrolling content, sliding away as soon as
 * the content starts scrolling down and back as soon as it starts scrolling up. [content] gets the strip's full height to use as
 * its top padding, so the strip covers nothing when the content is at its top, and hiding or
 * showing the strip never changes how far the content can scroll:
 *
 *     § IV  Timeline ────────── 50 · 12     ← masthead, stays put
 *     ════════════════════════════════
 *     [⌕ search] [# tag · 3] [lanes]       ← over the content, slides away
 *     ────────────────────────────────
 *     <content, padded by the strip's height>
 *
 * Feed the content's scroll position to [HdScrollAwayStripState.followScrollPosition] so the strip is
 * always shown with the content at its top.
 */
@Composable
fun HdScrollAwayStrip(
	state: HdScrollAwayStripState,
	strip: @Composable ColumnScope.() -> Unit,
	modifier: Modifier = Modifier,
	content: @Composable (topPadding: Dp) -> Unit,
) {
	Box(modifier = modifier.nestedScroll(state.nestedScrollConnection).clipToBounds()) {
		content(with(LocalDensity.current) { state.height.toDp() })
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.onSizeChanged { size -> state.onHeightChanged(size.height) }
				.graphicsLayer { translationY = state.offset }
				.testTag(HD_SCROLL_AWAY_STRIP_TAG)
				.background(MaterialTheme.colorScheme.surface),
			content = strip,
		)
	}
}
