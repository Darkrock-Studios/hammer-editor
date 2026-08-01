package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Width the scrollbar occupies at the scrollable edge. Screens whose content runs edge to edge
 * should reserve this much trailing space so the bar does not sit on top of it. Zero on platforms
 * whose scrollbar is a transient overlay.
 */
expect val MpScrollBarGutter: Dp

@Composable
expect fun MpScrollBarList(
	modifier: Modifier = Modifier.fillMaxHeight(),
	state: LazyListState
)

@Composable
expect fun MpScrollBarColumn(
	modifier: Modifier = Modifier.fillMaxHeight(),
	state: ScrollState
)

@Composable
expect fun MpScrollBarGrid(
	modifier: Modifier = Modifier.fillMaxHeight(),
	state: LazyGridState
)

@Composable
expect fun MpScrollBarStaggeredGrid(
	modifier: Modifier = Modifier.fillMaxHeight(),
	state: LazyStaggeredGridState
)
