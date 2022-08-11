package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.tree.TreeValue
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun LayoutCoordinates.positionIn(ancestor: LayoutCoordinates): Offset {
    return ancestor.windowToLocal(positionInWindow())
}

fun findItemId(
    dragOffset: Offset,
    layouts: HashMap<Int, LayoutCoordinates>,
    tree: ImmutableTree<SceneItem>,
    selectedId: Int,
    columnLayoutInfo: LayoutCoordinates
): Int {
    val selectedItem = layouts[selectedId] ?: return -1
    val selectedLocalPos = selectedItem.positionIn(columnLayoutInfo)

    val y = dragOffset.y + selectedLocalPos.y

    val selectedItemIndex = tree.indexOf { it.id == selectedId }

    var foundItemId: Int = -1
    for ((id, layout) in layouts.entries) {
        if (!layout.isAttached) continue

        val size = layout.size
        val pos = layout.positionIn(columnLayoutInfo)

        if (id != selectedId && y >= pos.y && y <= (pos.y + size.height)) {
            val leafIndex = tree.indexOf { it.id == id }
            val isAncestorOf =
                tree.isAncestorOf(needleIndex = selectedItemIndex, leafIndex = leafIndex)
            if (!isAncestorOf) {
                foundItemId = id
                break
            }
        }
    }
    return foundItemId
}

@Composable
fun SceneTree(
    summary: SceneSummary?,
    modifier: Modifier,
    itemUi: ItemUi
) {
    summary ?: return

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val nodeLayouts = remember { HashMap<Int, LayoutCoordinates>() }
    var insertBelowId by remember { mutableStateOf(-1) }

    var initialScroll by remember { mutableStateOf(0) }
    var offsetY by remember { mutableStateOf(0f) }
    var selectedId by remember { mutableStateOf(-1) }

    val startDragging = { id: Int ->
        if (selectedId == -1) {
            Napier.d("startDragging ID: $id")
            selectedId = id
            initialScroll = scrollState.value
        }
    }
    val stopDragging = {
        Napier.d("stopDragging")
        selectedId = -1
        offsetY = 0f
        initialScroll = 0
        insertBelowId = -1
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
            summary = summary,
            nodeLayouts = nodeLayouts,
            containerLayoutInfo = { containerLayoutInfo },
            columnLayoutInfo = { columnLayoutInfo },
            startDragging = startDragging,
            stopDragging = stopDragging,
            selectedId = { selectedId },
            offsetY = { offsetY },
            scrollState = { scrollState },
            autoScroll = autoScroll,
            initialScroll = initialScroll,
            setOffsetY = { offsetY += it }
        ) { insertBelowId = it }
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
                    tree = childNode,
                    selectedId = selectedId,
                    draggableFactory = draggableFactory,
                    itemUi = itemUi
                )
            }
        }

        // Draw insert line
        if (insertBelowId > -1) {
            nodeLayouts[insertBelowId]?.let { insertBelowLayout ->

                val node = summary.sceneTree.findBy { it.id == insertBelowId }
                val localOffset = columnLayoutInfo?.let { insertBelowLayout.positionIn(it) }

                if (node != null && localOffset != null) {
                    val lineY = localOffset.y + insertBelowLayout.size.height - scrollState.value

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

fun dragModifier(
    childNode: TreeValue<SceneItem>,
    summary: SceneSummary,
    nodeLayouts: HashMap<Int, LayoutCoordinates>,
    containerLayoutInfo: () -> LayoutCoordinates?,
    columnLayoutInfo: () -> LayoutCoordinates?,
    startDragging: (Int) -> Unit,
    stopDragging: () -> Unit,
    selectedId: () -> Int,
    offsetY: () -> Float,
    scrollState: (() -> ScrollState),
    autoScroll: (container: LayoutCoordinates, up: Boolean) -> Unit,
    initialScroll: Int,
    setOffsetY: (offset: Float) -> Unit,
    setInsertBelowId: (insertBelowId: Int) -> Unit,
): Modifier {
    var draggable = Modifier
        .onGloballyPositioned { coordinates ->
            nodeLayouts[childNode.value.id] = coordinates
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    startDragging(childNode.value.id)
                },
                onDragEnd = {
                    stopDragging()
                }
            ) { change, dragAmount ->
                if (selectedId() == childNode.value.id) {
                    setOffsetY(dragAmount.y)
                }

                val containerInfo = containerLayoutInfo()
                val columnInfo = columnLayoutInfo()
                if (containerInfo != null && columnInfo != null) {
                    val insertBelowId =
                        findItemId(
                            change.position,
                            nodeLayouts,
                            summary.sceneTree,
                            selectedId(),
                            columnInfo
                        )
                    setInsertBelowId(insertBelowId)

                    // Auto scroll test
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
                }
            }
        }

    if (selectedId() == childNode.value.id) {
        draggable = draggable.offset {
            return@offset IntOffset(
                x = 0,
                y = offsetY().roundToInt() + (scrollState().value - initialScroll)
            )
        }
    }

    return draggable
}

@Composable
fun SceneTreeNode(
    tree: TreeValue<SceneItem>,
    selectedId: Int,
    draggableFactory: ((TreeValue<SceneItem>) -> Modifier)?,
    itemUi: ItemUi
) {
    var collapsed by remember { mutableStateOf(false) }

    val toggleExpanded = {
        collapsed = !collapsed
    }

    var dragModifier = draggableFactory?.invoke(tree) ?: Modifier
    if (tree.value.id == selectedId) {
        dragModifier = dragModifier.zIndex(2f).alpha(0.5f)
    }
    itemUi(tree, toggleExpanded, dragModifier)

    AnimatedVisibility(visible = !collapsed) {
        tree.children.forEachIndexed { index, child ->
            SceneTreeNode(
                tree = child,
                selectedId = selectedId,
                draggableFactory = if (!collapsed) draggableFactory else null,
                itemUi = itemUi,
            )
        }
    }
}

typealias ItemUi = @Composable (
    node: TreeValue<SceneItem>,
    toggleExpanded: () -> Unit,
    draggable: Modifier,
) -> Unit