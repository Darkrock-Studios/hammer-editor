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
import com.darkrockstudios.apps.hammer.common.data.SceneDef
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
fun SceneListUi(
    component: SceneList,
    modifier: Modifier = Modifier
) {
    val state by component.state.subscribeAsState()
    var newSceneNameText by remember { mutableStateOf("") }
    var sceneDefDeleteTarget by remember { mutableStateOf<SceneDef?>(null) }

    val reorderState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val newOrder = state.sceneDefs.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            component.updateSceneOrder(newOrder)
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
            items(state.sceneDefs.size, { state.sceneDefs[it].hashCode() }) { item ->
                val scene = state.sceneDefs[item]

                ReorderableItem(reorderState, key = scene.hashCode()) { isDragging ->
                    val elevation = animateDpAsState(if (isDragging) 16.dp else 0.dp)
                    Column(
                        modifier = Modifier
                            .shadow(elevation.value)
                            .background(MaterialTheme.colors.surface)
                    ) {
                        val isSelected = scene == state.selectedSceneDef
                        SceneItem(scene, isSelected, component::onSceneSelected) { selectedScene ->
                            sceneDefDeleteTarget = selectedScene
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SceneItem(
    sceneDef: SceneDef,
    isSelected: Boolean,
    onSceneSelected: (SceneDef) -> Unit,
    onSceneAltClick: (SceneDef) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Ui.PADDING)
            .run { if (isSelected) background(color = selectionColor()) else this }
            .combinedClickable(
                onClick = { onSceneSelected(sceneDef) },
                //onLongClick = { onSceneAltClick(scene) }
            ),
        elevation = Ui.ELEVATION
    ) {
        Row(
            modifier = Modifier.padding(Ui.PADDING).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Scene: ${sceneDef.name}",
                style = MaterialTheme.typography.body1
            )
            Button({ onSceneAltClick(sceneDef) }) {
                Text("X")
            }
        }
    }
}

@Composable
private fun selectionColor(): Color =
    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
fun sceneDeleteDialog(sceneDef: SceneDef, dismissDialog: (Boolean) -> Unit) {
    AlertDialog(
        title = { Text("Delete Scene") },
        text = { Text("Are you sure you want to delete this scene: ${sceneDef.name}") },
        onDismissRequest = { dismissDialog(false) },
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