package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree.SceneTree
import com.darkrockstudios.apps.hammer.common.tree.TreeValue

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
fun SceneListUi(
    component: SceneList,
    modifier: Modifier = Modifier
) {
    val state by component.state.subscribeAsState()
    var newSceneItemNameText by remember { mutableStateOf("") }
    var sceneDefDeleteTarget by remember { mutableStateOf<SceneItem?>(null) }

    val summary = state.scenes

    Column(modifier = modifier.fillMaxWidth().padding(Ui.PADDING)) {
        TextField(
            value = newSceneItemNameText,
            onValueChange = { newSceneItemNameText = it },
            label = { Text("New Scene Name") }
        )
        Row {
            ExtendedFloatingActionButton(
                onClick = {
                    component.createScene(newSceneItemNameText)
                    newSceneItemNameText = ""
                },
                text = { Text("Create Scene") },
                icon = { Icon(Icons.Filled.Add, "") },
                modifier = Modifier.padding(top = Ui.PADDING)
            )
            ExtendedFloatingActionButton(
                onClick = {
                    component.createGroup(newSceneItemNameText)
                    newSceneItemNameText = ""
                },
                text = { Text("Create Group") },
                icon = { Icon(Icons.Filled.Add, "") },
                modifier = Modifier.padding(top = Ui.PADDING)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = Ui.PADDING),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "\uD83D\uDCDD Scenes:",
                style = MaterialTheme.typography.h5,
            )
        }

        if (summary != null) {
            SceneTree(
                summary = summary,
                modifier = Modifier.fillMaxSize(),
                moveItem = component::moveScene
            ) { sceneNode, toggleExpand, draggable ->
                SceneNode(
                    sceneNode = sceneNode,
                    draggable = draggable,
                    state = state,
                    summary = summary,
                    component = component,
                    toggleExpand = toggleExpand,
                    sceneDefDeleteTarget = { deleteTarget ->
                        sceneDefDeleteTarget = deleteTarget
                    },
                )
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
fun SceneNode(
    sceneNode: TreeValue<SceneItem>,
    draggable: Modifier,
    state: SceneList.State,
    summary: SceneSummary,
    component: SceneList,
    toggleExpand: () -> Unit,
    sceneDefDeleteTarget: (SceneItem) -> Unit,
) {
    val scene = sceneNode.value
    val isSelected = scene == state.selectedSceneItem
    if (scene.type == SceneItem.Type.Scene) {
        SceneItem(
            scene = scene,
            draggable = draggable,
            depth = sceneNode.depth,
            hasDirtyBuffer = summary.hasDirtyBuffer.contains(scene.id),
            isSelected = isSelected,
            onSceneSelected = component::onSceneSelected,
            onSceneAltClick = { selectedScene ->
                sceneDefDeleteTarget(selectedScene)
            },
        )
    } else {
        SceneGroupItem(
            sceneNode = sceneNode,
            draggable = draggable,
            hasDirtyBuffer = summary.hasDirtyBuffer,
            toggleExpand = toggleExpand,
            onSceneAltClick = { selectedScene ->
                sceneDefDeleteTarget(selectedScene)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SceneItem(
    scene: SceneItem,
    draggable: Modifier,
    depth: Int,
    hasDirtyBuffer: Boolean,
    isSelected: Boolean,
    onSceneSelected: (SceneItem) -> Unit,
    onSceneAltClick: (SceneItem) -> Unit,
) {
    Card(
        modifier = draggable
            .fillMaxWidth()
            .padding(
                start = (Ui.PADDING + (Ui.PADDING * (depth - 1) * 2)),
                top = Ui.PADDING,
                bottom = Ui.PADDING,
                end = Ui.PADDING
            )
            .combinedClickable(
                onClick = { onSceneSelected(scene) },
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
                "Scene: ${scene.id} - ${scene.name}",
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

@Composable
fun SceneGroupItem(
    sceneNode: TreeValue<SceneItem>,
    draggable: Modifier,
    hasDirtyBuffer: Set<Int>,
    toggleExpand: () -> Unit,
    onSceneAltClick: (SceneItem) -> Unit,
) {
    val (scene: SceneItem, _, _, children: List<TreeValue<SceneItem>>) = sceneNode

    Card(
        modifier = draggable
            .fillMaxWidth()
            .padding(
                start = (Ui.PADDING + (Ui.PADDING * (sceneNode.depth - 1) * 2)),
                top = Ui.PADDING,
                bottom = Ui.PADDING,
                end = Ui.PADDING
            )
            .clickable(onClick = toggleExpand),
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
            if (children.isEmpty()) {
                Button({ onSceneAltClick(scene) }) {
                    Text("X")
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