package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

actual val MpScrollBarGutter: Dp = 0.dp

@Composable
actual fun MpScrollBarList(
	modifier: Modifier,
	state: LazyListState
) {
	// iOS shows inline scroll indicators natively; no explicit scrollbar composable needed.
}

@Composable
actual fun MpScrollBarColumn(
	modifier: Modifier,
	state: ScrollState
) {
	// iOS shows inline scroll indicators natively; no explicit scrollbar composable needed.
}

@Composable
actual fun MpScrollBarGrid(
	modifier: Modifier,
	state: LazyGridState
) {
	// iOS shows inline scroll indicators natively; no explicit scrollbar composable needed.
}

@Composable
actual fun MpScrollBarStaggeredGrid(
	modifier: Modifier,
	state: LazyStaggeredGridState
) {
	// iOS shows inline scroll indicators natively; no explicit scrollbar composable needed.
}
