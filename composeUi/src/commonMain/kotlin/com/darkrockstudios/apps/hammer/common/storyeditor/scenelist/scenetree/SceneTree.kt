package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdIndentFor

/**
 * The root composable take takes a scene tree and handles rendering, reorder, collapsing
 * of the entire tree
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SceneTree(
	state: SceneTreeState,
	modifier: Modifier,
	itemUi: ItemUi,
	contentPadding: PaddingValues
) {
	Box {
		Row {
			if (state.summary.sceneTree.totalNodes <= 1) {
				Text(
					text = "No Scenes",
					modifier = Modifier.fillMaxWidth().padding(Ui.Padding.XL),
					textAlign = TextAlign.Center,
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onBackground
				)
			} else {
				LazyColumn(
					state = state.listState,
					modifier = modifier.reorderableModifier(state)
						.onGloballyPositioned { state.setListCoordinates(it) }
						.weight(1f),
					contentPadding = contentPadding
				) {
					items(
						items = state.visibleNodes,
						key = { it.value.id },
						contentType = { it.value.type }
					) { node ->
						val nodeCollapsesChildren =
							state.collapsedNodes[node.value.id] ?: false
						val id = node.value.id

						DisposableEffect(id) { onDispose { state.removeRowCoordinates(id) } }

						SceneTreeNode(
							node = node,
							nodeCollapsesChildren = nodeCollapsesChildren,
							selectedId = state.selectedId,
							toggleExpanded = state::toggleExpanded,
							modifier = Modifier.wrapContentHeight()
								.fillMaxWidth()
								.animateItem()
								.onGloballyPositioned { state.setRowCoordinates(id, it) }
								.pressCandidate(state, id),
							itemUi = itemUi
						)
					}
				}
				MpScrollBarList(state = state.listState)
			}
		}
		drawInsertLine(state)
	}
}

/**
 * Claims this row as the drag target for as long as it is pressed.
 *
 * The row is settled at touch-down, not when the long press expires, so a list still settling
 * under a stationary finger hands back the row the user aimed at rather than whichever row has
 * since slid beneath them.
 */
private fun Modifier.pressCandidate(state: SceneTreeState, id: Int): Modifier =
	pointerInput(id) {
		awaitEachGesture {
			val down = awaitFirstDown(requireUnconsumed = false)
			if (!state.claimPress(down.id, id)) return@awaitEachGesture

			// The release must survive this row being disposed mid-press, which cancels us.
			try {
				var change: PointerInputChange?
				do {
					change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
				} while (change?.pressed == true)
			} finally {
				state.releasePress(down.id)
			}
		}
	}

@Composable
private fun Modifier.reorderableModifier(state: SceneTreeState): Modifier {
	val hapticFeedback = LocalHapticFeedback.current
	state.apply {
		return pointerInput(Unit) {
			detectDragGesturesAfterLongPress(
				onDragStart = { offset ->
					val id = pressedRowId
					if (id != SceneTreeState.NO_SELECTION) {
						hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
						startDragging(id)
						updateDragPosition(offset)
						autoScrollForDrag()
					}
				},
				onDragCancel = {
					stopDragging()
				},
				onDragEnd = {
					stopDragging()
				}
			) { change, _ ->
				change.consume()
				updateDragPosition(change.position)
				autoScrollForDrag()
			}
		}
	}
}

private val EDGE_INSET = 16f.dp

@Composable
private fun drawInsertLine(
	state: SceneTreeState,
	color: Color = MaterialTheme.colorScheme.secondary
) {
	// All state reads happen inside the draw lambda so per-frame layout changes during a
	// drag (autoscroll, reorder animations) invalidate only the draw phase, not composition.
	Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
		val insertPos = state.insertAt ?: return@Canvas
		val tree = state.summary.sceneTree

		val intoEmptyGroup = insertPos.coords.globalIndex < 0
		val node = try {
			if (intoEmptyGroup) {
				// Inserting into empty group - use parent node
				tree[insertPos.coords.parentIndex]
			} else {
				tree[insertPos.coords.globalIndex]
			}
		} catch (
			@Suppress(
				"TooGenericExceptionCaught",
				"SwallowedException"
			) e: IndexOutOfBoundsException
		) { // stale insert coords: skip drawing
			return@Canvas
		}

		val visibleItems = state.rowLayouts
		val anchorLayout = visibleItems.find { it.id == node.value.id }

		val lineY: Float
		val nestingDept: Int
		if (anchorLayout != null) {
			lineY = if (insertPos.before) anchorLayout.top else anchorLayout.bottom

			val isGroup = node.value.type.isCollection
			val isCollapsed = (state.collapsedNodes[node.value.id] == true)
			nestingDept = if (intoEmptyGroup || (isGroup && !insertPos.before && !isCollapsed)) {
				node.depth + 1
			} else {
				node.depth
			}
		} else {
			// Anchor row hidden inside a collapsed group: mark the drop at the group's
			// bottom edge, inset one level to read as "inside". An anchor that is merely
			// scrolled out of the viewport gets no line.
			if (node.parent < 0) return@Canvas
			val hiddenByCollapse = tree.getBranch(node.index, true)
				.any { state.collapsedNodes[it.value.id] == true }
			if (!hiddenByCollapse) return@Canvas
			val parentNode = tree[node.parent]
			val parentLayout = visibleItems.find { it.id == parentNode.value.id } ?: return@Canvas
			lineY = parentLayout.bottom
			nestingDept = parentNode.depth + 1
		}

		val insetSize = hdIndentFor(nestingDept).toPx()
		val endX = size.width - EDGE_INSET.toPx()

		drawLine(
			start = Offset(x = insetSize, y = lineY),
			end = Offset(x = endX, y = lineY),
			color = color,
			strokeWidth = 5f.dp.toPx(),
			cap = StrokeCap.Round
		)
	}
}