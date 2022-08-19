package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The root composable take takes a scene tree and handles rendering, reorder, collapsing
 * of the entire tree
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SceneTree(
    summary: SceneSummary?,
    modifier: Modifier,
    moveItem: (moveRequest: MoveRequest) -> Unit,
    itemUi: ItemUi
) {
    summary ?: return

    val collapsedNodes = remember { mutableStateMapOf<Int, Boolean>() }
    var dragId by remember { mutableStateOf(0) }

    var treeHash by remember { mutableStateOf(summary.sceneTree.hashCode()) }
    val newHash = summary.sceneTree.hashCode()
    if (treeHash != newHash) {
        treeHash = newHash
        dragId += 1

        // Prune layouts if the id is not found in the tree
        val nodeIt = collapsedNodes.iterator()
        while (nodeIt.hasNext()) {
            val (id, _) = nodeIt.next()
            val foundNode = summary.sceneTree.findBy { it.id == id }
            if (foundNode == null) {
                nodeIt.remove()
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    var selectedId by remember { mutableStateOf(-1) }
    var insertAt by remember { mutableStateOf<InsertPosition?>(null) }
    val listState = rememberLazyListState()

    val startDragging = { id: Int ->
        if (selectedId == -1) {
            selectedId = id
        }
    }
    val stopDragging = {
        val insertPosition = insertAt
        val selectedIndex = summary.sceneTree.indexOf { it.id == selectedId }
        if (selectedIndex > 0 && insertPosition != null) {
            val request = MoveRequest(
                selectedId,
                insertPosition
            )
            moveItem(request)
        }

        selectedId = -1
        insertAt = null
        dragId += 1
    }

    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val autoScroll = { up: Boolean ->
        val previousIndex = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
        if (scrollJob?.isActive != true) {
            scrollJob = if (up) {
                coroutineScope.launch {
                    listState.animateScrollToItem(previousIndex)
                }
            } else {
                coroutineScope.launch {
                    listState.layoutInfo.apply {
                        val viewportHeight = viewportEndOffset + viewportStartOffset
                        val index = visibleItemsInfo.size + previousIndex
                        val lastInfo = visibleItemsInfo[visibleItemsInfo.size - 1]
                        val offset = lastInfo.size - viewportHeight

                        listState.animateScrollToItem(index, offset)
                    }
                }
            }
        }
    }

    val toggleExpanded = { nodeId: Int ->
        val collapse = !(collapsedNodes[nodeId] ?: false)
        collapsedNodes[nodeId] = collapse
    }

    Box(modifier = Modifier) {
        LazyColumn(
            state = listState,
            modifier = modifier.pointerInput(dragId) {
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

                    // Auto scroll test
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
            },
        ) {
            items(
                count = summary.sceneTree.totalChildren,
                key = { summary.sceneTree[it].value.id },
                //contentType = { summary.sceneTree[it].value.type }
            ) { index ->
                val childNode = summary.sceneTree[index]
                val shouldCollapse = if (collapsedNodes.isEmpty()) {
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

                if (!childNode.value.isRootScene) {
                    SceneTreeNode(
                        node = childNode,
                        collapsed = shouldCollapse, // need to take parent into account
                        selectedId = selectedId,
                        toggleExpanded = toggleExpanded,
                        modifier = Modifier.wrapContentHeight()
                            .fillMaxWidth()
                            .animateItemPlacement(),
                        itemUi = itemUi
                    )
                }
            }
        }

        // Draw insert line
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
                    val nestingInset = 32f
                    val nestingDept = if (isGroup && !insertPos.before && !isCollapsed) {
                        node.depth + 1
                    } else {
                        node.depth
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width

                        val insetSize = (nestingDept * nestingInset)
                        val endX = canvasWidth - nestingInset

                        drawLine(
                            start = Offset(x = insetSize.dp.toPx(), y = lineY.toFloat()),
                            end = Offset(x = endX.dp.toPx(), y = lineY.toFloat()),
                            color = Color.Black,
                            strokeWidth = 5f.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
        }
    }
}