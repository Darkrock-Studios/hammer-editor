package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun MpScrollBarList(
	modifier: Modifier,
	state: LazyListState
) {
	VerticalScrollbar(
		modifier = modifier,
		adapter = rememberScrollbarAdapter(state)
	)
}

@Composable
actual fun MpScrollBar(modifier: Modifier, state: ScrollState) {
	VerticalScrollbar(
		modifier = modifier,
		adapter = rememberScrollbarAdapter(state)
	)
}