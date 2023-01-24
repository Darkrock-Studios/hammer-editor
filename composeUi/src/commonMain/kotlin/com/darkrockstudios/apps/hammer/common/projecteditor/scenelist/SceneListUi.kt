package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.common.compose.MpDialog
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree.SceneTree
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree.rememberReorderableLazyListState
import com.darkrockstudios.apps.hammer.common.tree.TreeValue
import io.github.aakira.napier.Napier

@OptIn(
	ExperimentalMaterialApi::class,
	ExperimentalComposeApi::class,
	ExperimentalMaterial3Api::class,
	ExperimentalFoundationApi::class
)
@Composable
fun SceneListUi(
	component: SceneList,
	modifier: Modifier = Modifier
) {
	val state by component.state.subscribeAsState()
	var sceneDefDeleteTarget by remember { mutableStateOf<SceneItem?>(null) }

	var showCreateGroupDialog by remember { mutableStateOf(false) }
	var showCreateSceneDialog by remember { mutableStateOf(false) }
	var expandOrCollapse by remember { mutableStateOf(false) }

	BoxWithConstraints {
		val summary = state.sceneSummary
		if (summary != null) {
			val treeState = rememberReorderableLazyListState(
				summary = summary,
				moveItem = component::moveScene
			)
			treeState.updateSummary(summary)

			Column(modifier = modifier.fillMaxSize()) {
				Row(
					modifier = Modifier.fillMaxWidth()
						.wrapContentHeight()
						.padding(Ui.Padding.L),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						"\uD83D\uDCDD Scenes:",
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onBackground,
						modifier = Modifier.weight(1f)
					)

					if (expandOrCollapse) {
						ElevatedButton(onClick = {
							treeState.expandAll()
							expandOrCollapse = false
						}) {
							Icon(Icons.Filled.ExpandMore, "Expand")
						}
					} else {
						ElevatedButton(onClick = {
							treeState.collapseAll()
							expandOrCollapse = true
						}) {
							Icon(Icons.Filled.ExpandLess, "Collapse")
						}
					}
				}

				Divider(modifier = Modifier.fillMaxWidth())

				SceneTree(
					modifier = Modifier.fillMaxSize(),
					state = treeState,
					itemUi = { node: TreeValue<SceneItem>,
							   toggleExpanded: (nodeId: Int) -> Unit,
							   collapsed: Boolean,
							   draggable: Modifier ->

						SceneNode(
							sceneNode = node,
							draggableModifier = draggable,
							state = state,
							summary = summary,
							component = component,
							toggleExpand = toggleExpanded,
							collapsed = collapsed,
							sceneDefDeleteTarget = { deleteTarget ->
								sceneDefDeleteTarget = deleteTarget
							}
						)
					},
					contentPadding = PaddingValues(bottom = 100.dp)
				)
			}
		}

		Row(modifier = Modifier.padding(Ui.Padding.L).align(Alignment.BottomEnd)) {
			FloatingActionButton(
				onClick = { showCreateGroupDialog = true },
				modifier = Modifier.padding(end = Ui.Padding.M)
			) {
				Icon(Icons.Filled.CreateNewFolder, "Create Group")
			}
			FloatingActionButton(onClick = { showCreateSceneDialog = true }) {
				Icon(Icons.Filled.PostAdd, "Create Scene")
			}
		}
	}

	CreateDialog(
		show = showCreateGroupDialog,
		title = "Create Group",
		textLabel = "Group Name"
	) { groupName ->
		Napier.d { "Create dialog close" }
		if (groupName != null) {
			component.createGroup(groupName)
		}
		showCreateGroupDialog = false
	}

	CreateDialog(
		show = showCreateSceneDialog,
		title = "Create Scene",
		textLabel = "Scene Name"
	) { sceneName ->
		Napier.d { "Create dialog close" }
		if (sceneName != null) {
			component.createScene(sceneName)
		}
		showCreateSceneDialog = false
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

@ExperimentalMaterial3Api
@Composable
private fun CreateDialog(
	show: Boolean,
	title: String,
	textLabel: String,
	onClose: (name: String?) -> Unit
) {
	MpDialog(
		visible = show,
		title = title,
		onCloseRequest = { onClose(null) }
	) {
		var nameText by remember { mutableStateOf("") }
		Column {
			TextField(
				value = nameText,
				onValueChange = { nameText = it },
				label = { Text(textLabel) }
			)

			Row {
				Button(onClick = { onClose(nameText) }) {
					Text("Create")
				}

				Button(onClick = { onClose(null) }) {
					Text("Cancel")
				}
			}
		}
	}
}

@ExperimentalFoundationApi
@Composable
private fun SceneNode(
	sceneNode: TreeValue<SceneItem>,
	draggableModifier: Modifier,
	state: SceneList.State,
	summary: SceneSummary,
	component: SceneList,
	toggleExpand: (nodeId: Int) -> Unit,
	collapsed: Boolean,
	sceneDefDeleteTarget: (SceneItem) -> Unit,
) {
	val scene = sceneNode.value
	val isSelected = scene == state.selectedSceneItem
	if (scene.type == SceneItem.Type.Scene) {
		SceneItem(
			scene = scene,
			draggable = draggableModifier,
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
			draggable = draggableModifier,
			hasDirtyBuffer = summary.hasDirtyBuffer,
			toggleExpand = toggleExpand,
			collapsed = collapsed,
			onSceneAltClick = { selectedScene ->
				sceneDefDeleteTarget(selectedScene)
			},
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxWithConstraintsScope.Unsaved(hasDirtyBuffer: Boolean) {
	if (hasDirtyBuffer) {
		Badge(modifier = Modifier.align(Alignment.TopEnd).padding(Ui.Padding.M))
	}
}

@Composable
internal fun selectionColor(): Color = MaterialTheme.colorScheme.tertiaryContainer

@ExperimentalMaterialApi
@ExperimentalComposeApi
@Composable
internal fun sceneDeleteDialog(scene: SceneItem, dismissDialog: (Boolean) -> Unit) {
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
		modifier = Modifier.width(300.dp).padding(Ui.Padding.XL)
	)
}