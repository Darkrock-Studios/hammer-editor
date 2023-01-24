package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.MpScrollBar
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
		itemUi: ItemUi
) {
	state.apply {
		Box {
			Row {
				LazyColumn(
						state = state.listState,
						modifier = reorderableModifier(state, modifier).weight(1f),
				) {
					items(
							count = summary.sceneTree.totalNodes,
							key = { summary.sceneTree[it].value.id },
							//contentType = { summary.sceneTree[it].value.type }
					) { index ->
						val childNode = summary.sceneTree[index]
						val shouldCollapseSelf = shouldCollapseNode(
							index,
							summary,
							collapsedNodes
						)
						val nodeCollapsesChildren = collapsedNodes[childNode.value.id] ?: false

						if (!childNode.value.isRootScene) {
							SceneTreeNode(
								node = childNode,
								collapsed = shouldCollapseSelf, // need to take parent into account
								nodeCollapsesChildren = nodeCollapsesChildren,
								selectedId = selectedId,
								toggleExpanded = state::toggleExpanded,
								modifier = Modifier.wrapContentHeight()
									.fillMaxWidth()
									.animateItemPlacement(),
								itemUi = itemUi
							)
						}
					}
				}
				MpScrollBar(state = state.listState)
			}
			drawInsertLine(state)
		}
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
private fun reorderableModifier(state: SceneTreeState, modifier: Modifier): Modifier {
	state.apply {
		return modifier.pointerInput(dragId) {
			detectDragGesturesAfterLongPress(
					onDragStart = { offset ->
						for (itemInfo in listState.layoutInfo.visibleItemsInfo) {
							if (offset.y >= itemInfo.offset && offset.y <= (itemInfo.offset + itemInfo.size)) {
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
				// TODO Compose 1.3.0 should have this field
				//val height = layoutInfo.viewportSize.height
				val height = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
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

private const val NESTING_INSET = 32f

@Composable
private fun drawInsertLine(state: SceneTreeState, color: Color = MaterialTheme.colorScheme.secondary) {
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

							val insetSize = (nestingDept * NESTING_INSET)
							val endX = canvasWidth - NESTING_INSET

							drawLine(
								start = Offset(x = insetSize.dp.toPx(), y = lineY.toFloat()),
								end = Offset(x = endX.dp.toPx(), y = lineY.toFloat()),
								color = color,
								strokeWidth = 5f.dp.toPx(),
								cap = StrokeCap.Round
							)
						}
					}
		}
	}
}