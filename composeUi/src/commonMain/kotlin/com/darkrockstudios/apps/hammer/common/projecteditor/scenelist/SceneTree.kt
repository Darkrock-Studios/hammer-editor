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
import com.darkrockstudios.apps.hammer.common.data.InsertPosition
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.tree.NodeCoordinates
import com.darkrockstudios.apps.hammer.common.tree.TreeValue
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun LayoutCoordinates.positionIn(ancestor: LayoutCoordinates): Offset {
    return ancestor.windowToLocal(positionInWindow())
}

private fun findItemId(
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

private fun findInsertPosition(
    dragOffset: Offset,
    layouts: HashMap<Int, LayoutCoordinates>,
    tree: ImmutableTree<SceneItem>,
    selectedId: Int,
    columnLayoutInfo: LayoutCoordinates
): InsertPosition? {
    val selectedItem = layouts[selectedId] ?: return null
    val selectedLocalPos = selectedItem.positionIn(columnLayoutInfo)

    val dragY = dragOffset.y + selectedLocalPos.y

    val selectedItemIndex = tree.indexOf { it.id == selectedId }

    var foundItemId: InsertPosition? = null
    for ((id, layout) in layouts.entries) {
        if (!layout.isAttached) continue

        val size = layout.size
        val itemPos = layout.positionIn(columnLayoutInfo)

        if (id != selectedId && dragY >= itemPos.y && dragY <= (itemPos.y + size.height)) {
            val leafGlobalIndex = tree.indexOf { it.id == id }
            val isAncestorOf =
                tree.isAncestorOf(needleIndex = selectedItemIndex, leafIndex = leafGlobalIndex)
            if (!isAncestorOf) {
                // Decide above or below
                val halfHeight = size.height / 2f
                val localY = dragY - itemPos.y
                val before = localY < halfHeight

                //Napier.d("$localY < $halfHeight = before: $before")

                val leaf = tree[leafGlobalIndex]
                // Leaf is a group
                foundItemId = if (leaf.value.type.isCollection) {
                    // Insert above group
                    if (before) {
                        val coords = tree.getCoordinatesFor(leaf)
                        InsertPosition(coords, true)
                    }
                    // Insert as first item in group
                    else {
                        if (leaf.children.isNotEmpty()) {
                            val coords = tree.getCoordinatesFor(leaf.children[0])
                            Napier.d(coords.toString())
                            InsertPosition(coords, true)
                        } else {
                            val coords = NodeCoordinates(
                                globalIndex = leaf.index + 1,
                                parentIndex = leaf.index,
                                childLocalIndex = 0
                            )
                            Napier.d(coords.toString())
                            InsertPosition(coords, false)
                        }
                    }
                }
                // Leaf is just a leaf
                else {
                    val coords = tree.getCoordinatesFor(leaf)
                    InsertPosition(coords, before)
                }

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
    var count by remember { mutableStateOf(0) }

    val startDragging = { id: Int ->
        if (selectedId == -1) {
            Napier.d("startDragging ID: $id")
            selectedId = id
            initialScroll = scrollState.value
        }
    }
    val stopDragging = {
        Napier.d("stopDragging")

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
        count += 1
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
            count = count,
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

fun dragModifier(
    childNode: TreeValue<SceneItem>,
    summary: () -> SceneSummary,
    nodeLayouts: () -> HashMap<Int, LayoutCoordinates>,
    containerLayoutInfo: () -> LayoutCoordinates?,
    columnLayoutInfo: () -> LayoutCoordinates?,
    startDragging: (Int) -> Unit,
    stopDragging: () -> Unit,
    selectedId: () -> Int,
    offsetY: () -> Float,
    scrollState: () -> ScrollState,
    autoScroll: (container: LayoutCoordinates, up: Boolean) -> Unit,
    initialScroll: () -> Int,
    setOffsetY: (offset: Float) -> Unit,
    count: Int,
    setInsertAt: (insertAt: InsertPosition?) -> Unit,
): Modifier {
    val draggable = Modifier
        .onGloballyPositioned { coordinates ->
            nodeLayouts()[childNode.value.id] = coordinates
        }
        .pointerInput(count) {
            detectDragGestures(
                onDragStart = {
                    startDragging(childNode.value.id)
                },
                onDragEnd = {
                    stopDragging()
                }
            ) { change, dragAmount ->
                if (childNode.value.id == selectedId()) {
                    setOffsetY(dragAmount.y)
                }

                val containerInfo = containerLayoutInfo()
                val columnInfo = columnLayoutInfo()
                if (containerInfo != null && columnInfo != null) {
                    val insertPosition = findInsertPosition(
                        change.position,
                        nodeLayouts(),
                        summary().sceneTree,
                        selectedId(),
                        columnInfo
                    )

                    setInsertAt(insertPosition)

                    // Auto scroll test
                    nodeLayouts()[childNode.value.id]?.let { layout ->
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
        .offset {
            if ((childNode.value.id == selectedId())) {
                IntOffset(
                    x = 0,
                    y = offsetY().roundToInt() + (scrollState().value - initialScroll())
                )
            } else {
                IntOffset.Zero
            }
        }
        .zIndex(if ((childNode.value.id == selectedId())) 2f else 1f)
        .alpha(if ((childNode.value.id == selectedId())) 0.5f else 1f)

    return draggable
}

@Composable
fun SceneTreeNode(
    node: TreeValue<SceneItem>,
    selectedId: Int,
    draggableFactory: ((TreeValue<SceneItem>) -> Modifier)?,
    itemUi: ItemUi,
    collapsedNodes: HashMap<Int, Boolean>
) {
    var collapsed by remember(node.value.id) {
        mutableStateOf(
            collapsedNodes[node.value.id] ?: false
        )
    }

    val toggleExpanded = {
        val collapse = !(collapsedNodes[node.value.id] ?: false)
        collapsedNodes[node.value.id] = collapse
        collapsed = collapse
    }

    val dragModifier = draggableFactory?.invoke(node) ?: Modifier
    itemUi(node, toggleExpanded, dragModifier)

    node.children.forEach { child ->
        AnimatedVisibility(visible = !collapsed) {
            SceneTreeNode(
                node = child,
                selectedId = selectedId,
                draggableFactory = if (!collapsed) draggableFactory else null,
                itemUi = itemUi,
                collapsedNodes = collapsedNodes,
            )
        }
    }
}

typealias ItemUi = @Composable (
    node: TreeValue<SceneItem>,
    toggleExpanded: () -> Unit,
    draggable: Modifier,
) -> Unit