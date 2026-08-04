package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBarList
import com.darkrockstudios.apps.hammer.common.compose.Ui

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

						SceneTreeNode(
							node = node,
							nodeCollapsesChildren = nodeCollapsesChildren,
							selectedId = state.selectedId,
							toggleExpanded = state::toggleExpanded,
							// Placement stays un-animated: drag/drop hit-tests against
							// LazyListLayoutInfo, which reports target offsets, so a sliding
							// row would be grabbed by whatever now owns the spot it left.
							modifier = Modifier.wrapContentHeight()
								.fillMaxWidth()
								.animateItem(placementSpec = null),
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

@Composable
private fun Modifier.reorderableModifier(state: SceneTreeState): Modifier {
	val hapticFeedback = LocalHapticFeedback.current
	state.apply {
		return pointerInput(Unit) {
			detectDragGesturesAfterLongPress(
				onDragStart = { offset ->
					for (itemInfo in listState.layoutInfo.visibleItemsInfo) {
						if (offset.y >= itemInfo.offset && offset.y <= (itemInfo.offset + itemInfo.size)) {
							hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
							val id = itemInfo.key as Int
							startDragging(id)
							break
						}
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

				val layoutInfo: LazyListLayoutInfo = listState.layoutInfo
				val insertPosition = findInsertPosition(
					dragOffset = change.position,
					layouts = layoutInfo.visibleItemsInfo,
					collapsedGroups = collapsedNodes,
					tree = summary.sceneTree,
					visibleNodes = visibleNodes,
					selectedNode = selectedNode,
				)

				if (insertAt != insertPosition) {
					insertAt = insertPosition
				}

				// Auto scroll
				val height = layoutInfo.viewportSize.height - layoutInfo.viewportStartOffset
				val bottomTenPercent: Float = height * .9f
				val topTenPercent: Float = height * .1f

				if (change.position.y >= bottomTenPercent) {
					autoScroll(false)
				} else if (change.position.y <= topTenPercent) {
					autoScroll(true)
				}
			}
		}
	}
}

private val NESTING_INSET = 16f.dp

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

		val visibleItems = state.listState.layoutInfo.visibleItemsInfo
		val anchorLayout = visibleItems.find { it.key == node.value.id }

		val lineY: Int
		val nestingDept: Int
		if (anchorLayout != null) {
			lineY = if (insertPos.before) {
				anchorLayout.offset
			} else {
				anchorLayout.offset + anchorLayout.size
			}

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
			val parentLayout = visibleItems.find { it.key == parentNode.value.id } ?: return@Canvas
			lineY = parentLayout.offset + parentLayout.size
			nestingDept = parentNode.depth + 1
		}

		val insetSize = (nestingDept * NESTING_INSET.toPx())
		val endX = size.width - NESTING_INSET.toPx()

		drawLine(
			start = Offset(x = insetSize, y = lineY.toFloat()),
			end = Offset(x = endX, y = lineY.toFloat()),
			color = color,
			strokeWidth = 5f.dp.toPx(),
			cap = StrokeCap.Round
		)
	}
}