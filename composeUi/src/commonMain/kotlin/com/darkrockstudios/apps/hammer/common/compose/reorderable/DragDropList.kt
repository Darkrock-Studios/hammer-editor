package com.darkrockstudios.apps.hammer.common.compose.reorderable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarGutter
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.scrollBarOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> DragDropList(
	items: List<T>,
	key: ((index: Int, item: T) -> Any)? = null,
	onMove: (Int, Int) -> Unit,
	modifier: Modifier = Modifier,
	contentPadding: PaddingValues = PaddingValues(),
	lazyListState: LazyListState = rememberLazyListState(),
	itemContent: @Composable (item: T, dragging: Boolean) -> Unit
) {
	val scope = rememberCoroutineScope()

	var overscrollJob by remember { mutableStateOf<Job?>(null) }
	// Must be state-backed: it tracks the last external list so the guard below
	// resets the drag preview only on a real external change, not every recompose.
	var currentExternalList by remember { mutableStateOf(items) }

	var data by remember {
		mutableStateOf<List<T>>(
			items.toMutableList()
		)
	}

	if (items != currentExternalList) {
		currentExternalList = items
		data = items
	}

	// rememberDragDropListState captures its callbacks once, so route the
	// external one through rememberUpdatedState; otherwise it keeps invoking a
	// stale closure and moves the wrong item once the list order has changed.
	val currentOnMove by rememberUpdatedState(onMove)
	val dragDropListState = rememberDragDropListState(
		lazyListState = lazyListState,
		confirmReorder = { from, to -> currentOnMove(from, to) },
		onMove = { from, to ->
			data = data.toMutableList().apply {
				add(to, removeAt(from))
			}
		})

	val hapticFeedback = LocalHapticFeedback.current
	Box(modifier = modifier) {
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(end = MpScrollBarGutter)
				.pointerInput(Unit) {
					// A mouse picks a row up as soon as it moves; touch waits for a long press so it can scroll.
					// Either way the row is the one under the press, not wherever the pointer has got to since.
					awaitEachGesture {
						val down = awaitFirstDown(requireUnconsumed = false)
						val pickedUp = if (down.type == PointerType.Mouse) {
							awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
						} else {
							awaitLongPressOrCancellation(down.id)
						} ?: return@awaitEachGesture
						if (!dragDropListState.onDragStart(down.position)) return@awaitEachGesture
						hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

						fun dragBy(offset: Offset) {
							dragDropListState.onDrag(offset)
							if (overscrollJob?.isActive == true) return
							dragDropListState.checkForOverScroll()
								.takeIf { it != 0f }
								?.let { overscrollJob = scope.launch { dragDropListState.lazyListState.scrollBy(it) } }
								?: run { overscrollJob?.cancel() }
						}

						dragBy(pickedUp.position - down.position)
						val finished = drag(pickedUp.id) { change ->
							dragBy(change.positionChange())
							change.consume()
						}
						if (finished) dragDropListState.onDragEnd() else dragDropListState.onDragInterrupted()
					}
				},
			state = dragDropListState.lazyListState,
			contentPadding = contentPadding
		) {
			itemsIndexed(data, key) { index, item ->
				val isDragging = index == dragDropListState.currentIndexOfDraggedItem
				val isReordering = dragDropListState.currentIndexOfDraggedItem != null
				val zIndex = if (isDragging) {
					1f
				} else {
					0f
				}
				// Animate placement only while reordering, so the displaced items
				// slide into place; otherwise plain scrolling would animate them too.
				val itemModifier = when {
					isDragging -> Modifier.graphicsLayer {
						translationY = dragDropListState.elementDisplacement ?: 0f
					}
					isReordering -> Modifier.animateItem()
					else -> Modifier
				}
				Box(
					modifier = Modifier
						.zIndex(zIndex)
						.then(itemModifier)
				) {
					itemContent(item, isDragging)
				}
			}
		}

		MpScrollBarList(
			modifier = scrollBarOverlay(),
			state = dragDropListState.lazyListState,
		)
	}
}