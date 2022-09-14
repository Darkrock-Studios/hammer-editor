package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
actual fun MpScrollBar(
		modifier: Modifier,
		state: LazyListState
) {
	val targetAlpha = if (state.isScrollInProgress) 1f else 0f
	val duration = if (state.isScrollInProgress) 150 else 500

	val alpha by animateFloatAsState(
			targetValue = targetAlpha,
			animationSpec = tween(durationMillis = duration)
	)

	val width: Dp = 6.dp

	Canvas(modifier = Modifier.width(width).fillMaxHeight()) {
		val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
		val needDrawScrollbar = state.isScrollInProgress || alpha > 0.0f

		// Draw scrollbar if scrolling or if the animation is still running and lazy column has content
		if (needDrawScrollbar && firstVisibleElementIndex != null) {
			val elementHeight = this.size.height / state.layoutInfo.totalItemsCount
			val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
			val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight

			drawRect(
					color = Color.Black,
					topLeft = Offset(this.size.width - width.toPx(), scrollbarOffsetY),
					size = Size(width.toPx(), scrollbarHeight),
					alpha = alpha
			)
		}
	}
}