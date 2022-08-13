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

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val nodeLayouts by remember { mutableStateOf(HashMap<Int, LayoutCoordinates>()) }
    val collapsedNodes by remember { mutableStateOf(HashMap<Int, Boolean>()) }

    var initialScroll by remember { mutableStateOf(0) }
    var offsetY by remember { mutableStateOf(0f) }
    var selectedId by remember { mutableStateOf(-1) }
    var insertAt by remember { mutableStateOf<InsertPosition?>(null) }
    var dragId by remember { mutableStateOf(0) }

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
        dragModifier(
            childNode = childNode,
            summary = { summary },
            nodeLayouts = { nodeLayouts },
            containerLayoutInfo = { containerLayoutInfo },
            columnLayoutInfo = { columnLayoutInfo },
            startDragging = startDragging,
            stopDragging = stopDragging,
            selectedId = { selectedId },
            offsetY = { offsetY },
            scrollState = { scrollState },
            autoScroll = autoScroll,
            initialScroll = { initialScroll },
            setOffsetY = { offsetY += it },
            count = dragId,
        ) { insertAt = it }
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

                if (localOffset != null) {
                    val lineY = if (node.value.type.isCollection
                        && node.children.isEmpty()
                        && !insertPos.before
                    ) {
                        localOffset.y + insertBelowLayout.size.height - scrollState.value
                    } else {
                        if (insertPos.before) {
                            localOffset.y - scrollState.value
                        } else {
                            localOffset.y + insertBelowLayout.size.height - scrollState.value
                        }
                    }

                    val isGroup = node.totalChildren > 0
                    val nestingDept = if (isGroup) node.depth + 1 else node.depth

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width

                        drawLine(
                            start = Offset(x = canvasWidth, y = lineY),
                            end = Offset(x = (nestingDept * 16f), y = lineY),
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