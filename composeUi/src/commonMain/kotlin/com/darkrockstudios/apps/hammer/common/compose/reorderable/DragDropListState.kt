package com.darkrockstudios.apps.hammer.common.compose.reorderable

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job

@Composable
fun rememberDragDropListState(
	lazyListState: LazyListState = rememberLazyListState(),
	confirmReorder: (Int, Int) -> Unit,
	onMove: (Int, Int) -> Unit,
): DragDropListState {
	return remember {
		DragDropListState(
			lazyListState = lazyListState,
			confirmReorder = confirmReorder,
			onMove = onMove
		)
	}
}

class DragDropListState(
	val lazyListState: LazyListState,
	private val confirmReorder: (Int, Int) -> Unit,
	private val onMove: (Int, Int) -> Unit
) {
	private var draggedDistance by mutableStateOf(0f)

	// used to obtain initial offsets on drag start
	private var initiallyDraggedElement by mutableStateOf<LazyListItemInfo?>(null)

	private var originalIndexOfDraggedItem by mutableStateOf<Int?>(null)

	var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)

	private val initialOffsets: Pair<Int, Int>?
		get() = initiallyDraggedElement?.let { Pair(it.offset, it.offsetEnd) }

	val elementDisplacement: Float?
		get() = currentIndexOfDraggedItem
			?.let { lazyListState.getVisibleItemInfoFor(absoluteIndex = it) }
			?.let { item -> (initiallyDraggedElement?.offset ?: 0f).toFloat() + draggedDistance - item.offset }

	private val currentElement: LazyListItemInfo?
		get() = currentIndexOfDraggedItem?.let {
			lazyListState.getVisibleItemInfoFor(absoluteIndex = it)
		}

	private var overscrollJob by mutableStateOf<Job?>(null)

	fun onDragStart(offset: Offset): Boolean {
		val itemInfo = lazyListState.layoutInfo.visibleItemsInfo
			.firstOrNull { item ->
				offset.y.toInt() in item.offset..(item.offset + item.size)
			}

		return if (itemInfo != null) {
			itemInfo.also {
				originalIndexOfDraggedItem = it.index
				currentIndexOfDraggedItem = it.index
				initiallyDraggedElement = it
			}
			true
		} else {
			false
		}
	}

	fun onDragEnd() {
		val from = originalIndexOfDraggedItem
		val to = currentIndexOfDraggedItem
		if (from != null && to != null && from != to) {
			confirmReorder(from, to)
		}
		onDragInterrupted()
	}

	fun onDragInterrupted() {
		draggedDistance = 0f
		originalIndexOfDraggedItem = null
		currentIndexOfDraggedItem = null
		initiallyDraggedElement = null
		overscrollJob?.cancel()
	}

	fun onDrag(offset: Offset) {
		draggedDistance += offset.y

		initialOffsets?.let { (topOffset, bottomOffset) ->
			val startOffset = topOffset + draggedDistance
			val endOffset = bottomOffset + draggedDistance

			currentElement?.let { hovered ->
				lazyListState.layoutInfo.visibleItemsInfo
					.filterNot { item -> item.offsetEnd < startOffset || item.offset > endOffset || hovered.index == item.index }
					.firstOrNull { item ->
						val delta = startOffset - hovered.offset
						when {
							delta > 0 -> (endOffset > item.offsetEnd)
							else -> (startOffset < item.offset)
						}
					}
					?.also { item ->
						currentIndexOfDraggedItem?.let { current ->
							onMove.invoke(current, item.index)
						}

						currentIndexOfDraggedItem = item.index
					}
			}
		}
	}

	fun checkForOverScroll(): Float {
		return initiallyDraggedElement?.let {
			val startOffset = it.offset + draggedDistance
			val endOffset = it.offsetEnd + draggedDistance

			return@let when {
				draggedDistance > 0 -> (endOffset - lazyListState.layoutInfo.viewportEndOffset).takeIf { diff -> diff > 0 }
				draggedDistance < 0 -> (startOffset - lazyListState.layoutInfo.viewportStartOffset).takeIf { diff -> diff < 0 }
				else -> null
			}
		} ?: 0f
	}
}