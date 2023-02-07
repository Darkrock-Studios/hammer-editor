package com.darkrockstudios.apps.hammer.common.compose.reorderable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> DragDropList(
	items: List<T>,
	key: ((index: Int, item: T) -> Any)? = null,
	onMove: (Int, Int) -> Unit,
	modifier: Modifier = Modifier,
	itemContent: @Composable (T) -> Unit
) {
	val scope = rememberCoroutineScope()

	var overscrollJob by remember { mutableStateOf<Job?>(null) }
	var currentExternalList = remember { items }

	var data by remember {
		mutableStateOf<List<T>>(
			items.toMutableList()
		)
	}

	if (items != currentExternalList) {
		currentExternalList = items
		data = items
	}

	val dragDropListState = rememberDragDropListState(
		confirmReorder = onMove,
		onMove = { from, to ->
			data = data.toMutableList().apply {
				add(to, removeAt(from))
			}
		})

	LazyColumn(
		modifier = modifier
			.pointerInput(Unit) {
				detectDragGestures(
					onDrag = { change, offset ->
						change.consume()
						dragDropListState.onDrag(offset)

						if (overscrollJob?.isActive == true)
							return@detectDragGestures

						dragDropListState.checkForOverScroll()
							.takeIf { it != 0f }
							?.let { overscrollJob = scope.launch { dragDropListState.lazyListState.scrollBy(it) } }
							?: run { overscrollJob?.cancel() }
					},
					onDragStart = { offset -> dragDropListState.onDragStart(offset) },
					onDragEnd = { dragDropListState.onDragEnd() },
					onDragCancel = { dragDropListState.onDragInterrupted() }
				)
			},
		state = dragDropListState.lazyListState
	) {
		itemsIndexed(data, key) { index, item ->
			val zIndex = if (index == dragDropListState.currentIndexOfDraggedItem) {
				1f
			} else {
				0f
			}
			Box(
				modifier = Modifier
					.zIndex(zIndex)
					.composed {
						if (index == dragDropListState.currentIndexOfDraggedItem) {
							graphicsLayer {
								translationY = dragDropListState.elementDisplacement ?: 0f
							}
						} else {
							animateItemPlacement()
						}
					}
			) {
				itemContent(item)
			}
		}
	}
}