package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.tree.ImmutableTree
import com.darkrockstudios.apps.hammer.common.tree.TreeValue
import io.github.aakira.napier.Napier
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

    /*
    Canvas(modifier = modifier.wrapContentHeight()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        drawLine(
            start = Offset(x = canvasWidth, y = 0f),
            end = Offset(x = 0f, y = canvasHeight),
            color = Color.Blue
        )
    }
    */
    //val interactionSource = remember { MutableInteractionSource() }

    //val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val nodeLayouts = remember { HashMap<Int, LayoutCoordinates>() }
    var insertBelowId by remember { mutableStateOf(-1) }

    var initialScroll by remember { mutableStateOf(0) }
    var offsetY by remember { mutableStateOf(0f) }
    var selectedId by remember { mutableStateOf(-1) }
    val startDragging = { id: Int ->
        Napier.d("startDragging")
        if (selectedId == -1) {
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

    Box {
        Column(
            modifier = modifier
                .verticalScroll(scrollState)
                .onGloballyPositioned { coordinates ->
                    columnLayoutInfo = coordinates
                },
        ) {

            summary.sceneTree.root.children.forEach { childNode ->

                var draggable = Modifier
                    .onGloballyPositioned { coordinates ->
                        //Napier.d("onGloballyPositioned ${childNode.value.id} - ${childNode.value.name}")
                        val x = coordinates.positionInParent()
                        //Napier.d("pos: $x")
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
                            if (selectedId == childNode.value.id) {
                                offsetY += dragAmount.y
                            }

                            columnLayoutInfo?.let { rootCoords ->
                                insertBelowId =
                                    findItemId(
                                        change.position,
                                        nodeLayouts,
                                        summary.sceneTree,
                                        selectedId,
                                        rootCoords
                                    )
                            }
                            // Auto scroll test
                            /*
                            val bottomTenPercent = heightIs.value * .9f
                            if (change.position.y >= bottomTenPercent) {
                                val tenPercent: Float = heightIs.value * .1f
                                coroutineScope.launch {
                                    scrollState.animateScrollBy(tenPercent)
                                }
                            }
                            */
                        }
                    }

                if (selectedId == childNode.value.id) {
                    draggable = draggable.offset {
                        return@offset if (selectedId == childNode.value.id) {
                            IntOffset(
                                x = 0,
                                y = offsetY.roundToInt() + (scrollState.value - initialScroll)
                            )
                        } else {
                            IntOffset(0, 0)
                        }
                    }
                }

                SceneTreeNode(
                    tree = childNode,
                    dragModifier = draggable,
                    itemUi = itemUi
                )
            }
        }

        // Draw insert line
        if (insertBelowId > -1) {
            nodeLayouts[insertBelowId]?.let { insertBelowLayout ->

                val node = summary.sceneTree.findBy { it.id == insertBelowId }

                val windowPosition = insertBelowLayout.positionInWindow()
                val localOffset = columnLayoutInfo?.windowToLocal(windowPosition)

                if (node != null && localOffset != null) {
                    val lineY = localOffset.y + insertBelowLayout.size.height - scrollState.value

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width

                        drawLine(
                            start = Offset(x = canvasWidth - (node.depth * 16), y = lineY),
                            end = Offset(x = 0f, y = lineY),
                            color = Color.Blue,
                            strokeWidth = 10f
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SceneTreeNode(
    tree: TreeValue<SceneItem>,
    dragModifier: Modifier,
    itemUi: ItemUi
) {
    /*
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        drawLine(color = Color.Blue, start = Offset(0f, 0f), end = Offset(width, 0f))
    }
    */
    var collapsed by remember { mutableStateOf(false) }

    val toggleExpanded = {
        collapsed = !collapsed
    }

    itemUi(tree, toggleExpanded, dragModifier)

    AnimatedVisibility(visible = !collapsed) {
        tree.children.forEachIndexed { index, child ->
            SceneTreeNode(
                tree = child,
                dragModifier = dragModifier.padding(start = Ui.PADDING * index),
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