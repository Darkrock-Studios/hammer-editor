package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The root composable take takes a scene tree and handles rendering, reorder, collapsing
 * of the entire tree
 */
@Composable
fun SceneTree(
    summary: SceneSummary?,
    modifier: Modifier,
    moveItem: (moveRequest: MoveRequest) -> Unit,
    itemUi: ItemUi
) {
    summary ?: return

    val nodeLayouts by remember { mutableStateOf(HashMap<Int, LayoutCoordinates>()) }
    val collapsedNodes = remember { mutableStateMapOf<Int, Boolean>() }
    var dragId by remember { mutableStateOf(0) }

    var treeHash by remember { mutableStateOf(summary.sceneTree.hashCode()) }
    val newHash = summary.sceneTree.hashCode()
    if (treeHash != newHash) {
        treeHash = newHash
        dragId += 1

        // Prune layouts if the id is not found in the tree
        val layoutIt = nodeLayouts.iterator()
        while (layoutIt.hasNext()) {
            val (id, _) = layoutIt.next()
            val foundNode = summary.sceneTree.findBy { it.id == id }
            if (foundNode == null) {
                layoutIt.remove()
                collapsedNodes.remove(id)
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    var initialScroll by remember { mutableStateOf(0) }
    var offsetY by remember { mutableStateOf(0f) }
    var selectedId by remember { mutableStateOf(-1) }
    var insertAt by remember { mutableStateOf<InsertPosition?>(null) }
    val listState = rememberLazyListState()

    var columnLayoutInfo by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var containerLayoutInfo by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val scroll = 0

    val startDragging = { id: Int ->
        if (selectedId == -1) {
            selectedId = id
            initialScroll = scroll
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
        offsetY = 0f
        initialScroll = 0
        insertAt = null
        dragId += 1
    }

    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val autoScroll = { containerInfo: LayoutCoordinates, up: Boolean ->
        if (scrollJob?.isActive != true) {
            var scrollAmount: Float = containerInfo.size.height * .2f
            if (up) {
                scrollAmount *= -1f
            }
            scrollJob = coroutineScope.launch {
                listState.animateScrollBy(scrollAmount)
            }
        }
    }

    val toggleExpanded = { nodeId: Int ->
        val collapse = !(collapsedNodes[nodeId] ?: false)
        collapsedNodes[nodeId] = collapse
    }

    Box(modifier = Modifier.onGloballyPositioned { coordinates ->
        containerLayoutInfo = coordinates
    }) {
        LazyColumn(
            state = listState,
            modifier = modifier
                .onGloballyPositioned { coordinates ->
                    columnLayoutInfo = coordinates
                }.pointerInput(dragId) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            for (itemInfo in listState.layoutInfo.visibleItemsInfo) {
                                if (offset.y >= itemInfo.offset && offset.y <= (itemInfo.offset + itemInfo.size)) {
                                    val id = itemInfo.key as Int
                                    Napier.d("Found node to start dragging: $id")
                                    startDragging(id)
                                    break
                                }
                            }
                        },
                        onDragEnd = {
                            stopDragging()
                        }
                    ) { change, _ ->
                        val insertPosition = findInsertPosition(
                            dragOffset = change.position,
                            layouts = listState.layoutInfo.visibleItemsInfo,
                            collapsedGroups = collapsedNodes,
                            tree = summary.sceneTree,
                            selectedId = selectedId,
                        )

                        if (insertAt != insertPosition) {
                            insertAt = insertPosition
                        }

                        // Auto scroll test
                        /*
                        nodeLayouts[childNode.value.id]?.let { layout ->
                            val windowPos = layout.localToWindow(change.position)
                            val localPos = containerInfo.windowToLocal(windowPos)

                            val bottomTenPercent = containerInfo.size.height * .9f
                            val topTenPercent = containerInfo.size.height * .1f
                            if (localPos.y >= bottomTenPercent) {
                                autoScroll(containerInfo, false)
                            } else if (localPos.y <= topTenPercent) {
                                autoScroll(containerInfo, true)
                            }
                        }
                        */
                        //}
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