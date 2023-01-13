package com.darkrockstudios.apps.hammer.common.compose

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun MpScrollBar(
		modifier: Modifier = Modifier.fillMaxHeight(),
		state: LazyListState
)