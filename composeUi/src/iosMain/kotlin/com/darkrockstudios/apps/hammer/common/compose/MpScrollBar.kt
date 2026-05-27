package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun MpScrollBarList(
	modifier: Modifier,
	state: LazyListState
) {
	// iOS shows inline scroll indicators natively; no explicit scrollbar composable needed.
}
