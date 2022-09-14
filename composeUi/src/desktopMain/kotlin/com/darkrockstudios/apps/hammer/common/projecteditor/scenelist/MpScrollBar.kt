package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun MpScrollBar(
		modifier: Modifier,
		state: LazyListState
) {
	VerticalScrollbar(
			modifier = modifier,
			adapter = rememberScrollbarAdapter(state)
	)
}