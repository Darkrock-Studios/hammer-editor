package com.darkrockstudios.apps.hammer.common.compose.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import kotlin.math.roundToInt

/**
 * The scroll-away filter strip of a browse screen: translates [content] by [scrollBehavior]'s
 * height offset and reduces the slot's reported height by the same amount, so a sibling scroll
 * container slides up under the disappearing strip. Borrowed from how M3's
 * [androidx.compose.material3.TopAppBar] internals collapse — applied to a non-app-bar element.
 *
 * Drive it with `TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())` and plumb
 * `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` on the parent column. The strip
 * hides on scroll-down and reveals on scroll-up; the masthead above it stays put.
 *
 *     § III  Notes ────────── 12 · 4        ← stays put
 *     ════════════════════════════════
 *     [⌕ search] [# tag · 3] [SORT ▾]       ← slides away on scroll
 *     ────────────────────────────────
 *     <scrolling content underneath>
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HdCollapsingStrip(
	scrollBehavior: TopAppBarScrollBehavior,
	content: @Composable () -> Unit,
) {
	Layout(
		// Opaque surface so the grid sliding underneath doesn't show through, and clipped so
		// the translated content can't bleed up into the section header above.
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface)
			.clipToBounds(),
		content = content,
	) { measurables, constraints ->
		val placeables = measurables.map { it.measure(constraints) }
		val totalHeight = placeables.sumOf { it.height }
		// Once we know the strip's total height, set the offset limit so it can hide entirely
		// but no further.
		scrollBehavior.state.heightOffsetLimit = -totalHeight.toFloat()
		val offset = scrollBehavior.state.heightOffset.roundToInt()
		val visibleHeight = (totalHeight + offset).coerceAtLeast(0)
		val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
		layout(width, visibleHeight) {
			var y = offset
			placeables.forEach { placeable ->
				placeable.place(0, y)
				y += placeable.height
			}
		}
	}
}
