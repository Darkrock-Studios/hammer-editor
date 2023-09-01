package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import com.darkrockstudios.apps.hammer.common.data.SceneSummary

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
						count = state.summary.sceneTree.totalNodes,
						key = { state.summary.sceneTree[it].value.id },
						contentType = { state.summary.sceneTree[it].value.type }
					) { index ->
						val childNode = state.summary.sceneTree[index]
						val shouldCollapseSelf = shouldCollapseNode(
							index,
							state.summary,
							state.collapsedNodes
						)
						val nodeCollapsesChildren =
							state.collapsedNodes[childNode.value.id] ?: false

						if (!childNode.value.isRootScene) {
							SceneTreeNode(
								node = childNode,
								collapsed = shouldCollapseSelf, // need to take parent into account
								nodeCollapsesChildren = nodeCollapsesChildren,
								selectedId = state.selectedId,
								toggleExpanded = state::toggleExpanded,
								modifier = Modifier.wrapContentHeight()
									.fillMaxWidth()
									.animateItemPlacement(),
								itemUi = itemUi
							)
						}
					}
				}
				MpScrollBarList(state = state.listState)
			}
		}
		drawInsertLine(state)
	}
}

private fun shouldCollapseNode(
	index: Int,
	summary: SceneSummary,
	collapsedNodes: SnapshotStateMap<Int, Boolean>
): Boolean {
	return if (collapsedNodes.isEmpty()) {
		false
	} else {
		val branch = summary.sceneTree.getBranch(index, true)
		if (branch.isEmpty()) {
			false
		} else {
			summary.sceneTree.getBranch(index, true)
				.map { collapsedNodes[it.value.id] == true }
				.reduce { acc, treeNodeCollapsed -> acc || treeNodeCollapsed }
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
					selectedId = selectedId,
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
	state.apply {
		insertAt?.let { insertPos ->
			val node = if (summary.sceneTree.totalChildren <= insertPos.coords.globalIndex) {
				summary.sceneTree.last()
			} else {
				summary.sceneTree[insertPos.coords.globalIndex]
			}

			listState.layoutInfo.visibleItemsInfo.find { it.key == node.value.id }
				?.let { insertBelowLayout ->

					val isGroup = node.value.type.isCollection
					val lineY = if (insertPos.before) {
						insertBelowLayout.offset
					} else {
						insertBelowLayout.offset + insertBelowLayout.size
					}

					val isCollapsed = (collapsedNodes[node.value.id] == true)
					val nestingDept = if (isGroup && !insertPos.before && !isCollapsed) {
						node.depth + 1
					} else {
						node.depth
					}

					Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
						val canvasWidth = size.width

						val insetSize = (nestingDept * NESTING_INSET.toPx())
						val endX = canvasWidth - NESTING_INSET.toPx()

						drawLine(
							start = Offset(x = insetSize, y = lineY.toFloat()),
							end = Offset(x = endX, y = lineY.toFloat()),
							color = color,
							strokeWidth = 5f.dp.toPx(),
							cap = StrokeCap.Round
						)
					}
				}
		}
	}
}