package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.tree.TreeValue
import org.burnoutcrew.reorderable.*

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
fun SceneListUi(
    component: SceneList,
    modifier: Modifier = Modifier
) {
    val state by component.state.subscribeAsState()
    var newSceneNameText by remember { mutableStateOf("") }
    var sceneDefDeleteTarget by remember { mutableStateOf<SceneItem?>(null) }

    val summary = state.scenes

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            if (summary != null) {
                val newOrder = summary.sceneTree.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                }
                // TODO scene ID
                //component.updateSceneOrder(newOrder)
            }
        },
        onDragEnd = { from, to ->
            component.moveScene(from = from, to = to)
        }
    )

    Column(modifier = modifier.fillMaxWidth().padding(Ui.PADDING)) {
        TextField(
            value = newSceneNameText,
            onValueChange = { newSceneNameText = it },
            label = { Text("New Scene Name") }
        )
        ExtendedFloatingActionButton(
            onClick = {
                component.createScene(newSceneNameText)
                newSceneNameText = ""
            },
            text = { Text("Create Scene") },
            icon = { Icon(Icons.Filled.Add, "") },
            modifier = Modifier.align(alignment = Alignment.End).padding(top = Ui.PADDING)
        )
        Row(
            modifier = Modifier.fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "\uD83D\uDCDD Scenes:",
                style = MaterialTheme.typography.h5,
            )
        }

        LazyColumn(
            state = reorderState.listState,
            modifier = Modifier.fillMaxHeight().fillMaxWidth().reorderable(reorderState)
                .detectReorderAfterLongPress(reorderState),
            contentPadding = PaddingValues(Ui.PADDING)
        ) {
            if (summary != null) {
                items(
                    summary.sceneTree.totalChildren,
                    { summary.sceneTree[it].value.id }) { item ->
                    val sceneNode = summary.sceneTree[item]
                    if (!sceneNode.value.isRootScene) {
                        ReorderableScene(
                            sceneNode,
                            reorderState,
                            state,
                            component
                        ) { deleteTarget ->
                            sceneDefDeleteTarget = deleteTarget
                        }
                    }
                }
            }
        }
    }

    sceneDefDeleteTarget?.let { scene ->
        sceneDeleteDialog(scene) { deleteScene ->
            if (deleteScene) {
                component.deleteScene(scene)
            }
            sceneDefDeleteTarget = null
        }
    }
}

@Composable
fun ReorderableScene(
    sceneNode: TreeValue<SceneItem>,
    reorderState: ReorderableLazyListState,
    state: SceneList.State,
    component: SceneList,
    sceneDefDeleteTarget: (SceneItem) -> Unit
) {
    val summary = state.scenes ?: return
    val (scene: SceneItem, children: Set<TreeValue<SceneItem>>) = sceneNode

    ReorderableItem(reorderState, key = scene.id) { isDragging ->
        val elevation = animateDpAsState(if (isDragging) 16.dp else 0.dp)
        Column(
            modifier = Modifier
                .shadow(elevation.value)
                .background(MaterialTheme.colors.surface)
        ) {
            val isSelected = scene == state.selectedSceneItem
            if (scene.type == SceneItem.Type.Scene) {
                SceneItem(
                    scene,
                    sceneNode.depth,
                    summary.hasDirtyBuffer.contains(scene.id),
                    isSelected,
                    component::onSceneSelected
                ) { selectedScene ->
                    sceneDefDeleteTarget(selectedScene)
                }
            } else {
                SceneGroupItem(
                    sceneNode,
                    summary.hasDirtyBuffer,
                    component::onSceneSelected
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SceneItem(
    scene: SceneItem,
    depth: Int,
    hasDirtyBuffer: Boolean,
    isSelected: Boolean,
    onSceneSelected: (SceneItem) -> Unit,
    onSceneAltClick: (SceneItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Ui.PADDING * (depth + 1)
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Ui.PADDING)
                .combinedClickable(
                    onClick = { onSceneSelected(scene) },
                    //onLongClick = { onSceneAltClick(scene) }
                ),
            elevation = Ui.ELEVATION,
            backgroundColor = if (isSelected) selectionColor() else MaterialTheme.colors.surface
        ) {
            Row(
                modifier = Modifier
                    .padding(Ui.PADDING)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Scene: ${scene.name}",
                    style = MaterialTheme.typography.body1
                )
                if (hasDirtyBuffer) {
                    Text(
                        "Unsaved",
                        style = MaterialTheme.typography.subtitle1
                    )
                }
                Button({ onSceneAltClick(scene) }) {
                    Text("X")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SceneGroupItem(
    sceneNode: TreeValue<SceneItem>,
    hasDirtyBuffer: Set<Int>,
    onSceneAltClick: (SceneItem) -> Unit
) {
    val (scene, children) = sceneNode

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Ui.PADDING * (sceneNode.depth + 1)
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Ui.PADDING)
                .combinedClickable(
                    onClick = { },
                    //onLongClick = { onSceneAltClick(scene) }
                ),
            elevation = Ui.ELEVATION,
            backgroundColor = MaterialTheme.colors.secondaryVariant
        ) {
            Row(
                modifier = Modifier.padding(Ui.PADDING).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Group: ${scene.name}",
                    style = MaterialTheme.typography.body1
                )

                if (children.any { hasDirtyBuffer.contains(it.value.id) }) {
                    Text(
                        "Unsaved",
                        style = MaterialTheme.typography.subtitle1
                    )
                }
                if (children.isNullOrEmpty()) {
                    Button({ onSceneAltClick(scene) }) {
                        Text("X")
                    }
                }
            }
        }
    }
}

@Composable
private fun selectionColor(): Color = MaterialTheme.colors.secondary

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
fun sceneDeleteDialog(scene: SceneItem, dismissDialog: (Boolean) -> Unit) {
    AlertDialog(
        title = { Text("Delete Scene") },
        text = { Text("Are you sure you want to delete this scene: ${scene.name}") },
        onDismissRequest = { /* noop */ },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = { dismissDialog(true) }) {
                    Text("DELETE")
                }
                Button(onClick = { dismissDialog(false) }) {
                    Text("Dismiss")
                }
            }
        },
        modifier = Modifier.width(300.dp).padding(Ui.PADDING)
    )
}