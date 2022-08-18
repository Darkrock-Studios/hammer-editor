package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.tree.TreeValue
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
    val collapsedNodes by remember { mutableStateOf(HashMap<Int, Boolean>()) }
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
    val scrollState = rememberScrollState()

    var initialScroll by remember { mutableStateOf(0) }
    var offsetY by remember { mutableStateOf(0f) }
    var selectedId by remember { mutableStateOf(-1) }
    var insertAt by remember { mutableStateOf<InsertPosition?>(null) }

    val startDragging = { id: Int ->
        if (selectedId == -1) {
            selectedId = id
            initialScroll = scrollState.value
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

    var columnLayoutInfo by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var containerLayoutInfo by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val autoScroll = { containerInfo: LayoutCoordinates, up: Boolean ->
        if (scrollJob?.isActive != true) {
            var scrollAmount: Float = containerInfo.size.height * .2f
            if (up) {
                scrollAmount *= -1f
            }
            scrollJob = coroutineScope.launch {
                scrollState.animateScrollBy(scrollAmount)
            }
        }
    }

    val draggableFactory = { childNode: TreeValue<SceneItem> ->
        val data = DragModifierData(
            childNode = childNode,
            summary = { summary },
            nodeLayouts = { nodeLayouts },
            containerLayoutInfo = { containerLayoutInfo },
            columnLayoutInfo = { columnLayoutInfo },
            collapsedNodes = collapsedNodes,
            startDragging = startDragging,
            stopDragging = stopDragging,
            selectedId = { selectedId },
            offsetY = { offsetY },
            scrollState = { scrollState },
            autoScroll = autoScroll,
            initialScroll = { initialScroll },
            setOffsetY = { offsetY += it },
            count = dragId,
            setInsertAt = { insertAt = it }
        )

        dragModifier(data)
    }

    Box(modifier = Modifier.onGloballyPositioned { coordinates ->
        containerLayoutInfo = coordinates
    }) {
        Column(
            modifier = modifier
                .verticalScroll(scrollState)
                .onGloballyPositioned { coordinates ->
                    columnLayoutInfo = coordinates
                },
        ) {
            summary.sceneTree.root.children.forEach { childNode ->
                SceneTreeNode(
                    node = childNode,
                    selectedId = selectedId,
                    draggableFactory = draggableFactory,
                    collapsedNodes = collapsedNodes,
                    itemUi = itemUi
                )
            }
        }

        // Draw insert line
        insertAt?.let { insertPos ->
            val node = if (summary.sceneTree.totalChildren <= insertPos.coords.globalIndex) {
                summary.sceneTree.last()
            } else {
                summary.sceneTree[insertPos.coords.globalIndex]
            }
            nodeLayouts[node.value.id]?.let { insertBelowLayout ->

                val localOffset = columnLayoutInfo?.let { columnInfo ->
                    if (columnInfo.isAttached && insertBelowLayout.isAttached) {
                        insertBelowLayout.positionIn(columnInfo)
                    } else {
                        null
                    }
                }

                val isGroup = node.value.type.isCollection
                if (localOffset != null) {
                    val lineY = if (insertPos.before) {
                        localOffset.y - scrollState.value
                    } else {
                        localOffset.y + insertBelowLayout.size.height - scrollState.value
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

                        drawLine(
                            start = Offset(x = insetSize, y = lineY),
                            end = Offset(x = canvasWidth - nestingInset, y = lineY),
                            color = Color.Black,
                            strokeWidth = 5f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}