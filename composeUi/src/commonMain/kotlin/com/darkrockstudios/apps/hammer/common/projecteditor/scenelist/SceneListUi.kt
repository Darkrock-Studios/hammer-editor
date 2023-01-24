package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
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
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.scenetree.rememberReorderableLazyListState
import com.darkrockstudios.apps.hammer.common.tree.TreeValue

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SceneListUi(
	component: SceneList,
	modifier: Modifier = Modifier
) {
	val state by component.state.subscribeAsState()
	var newSceneItemNameText by remember { mutableStateOf("") }
	var sceneDefDeleteTarget by remember { mutableStateOf<SceneItem?>(null) }

	val summary = state.sceneSummary
	if (summary != null) {
		val treeState = rememberReorderableLazyListState(
			summary = summary,
			moveItem = component::moveScene
		)
		treeState.updateSummary(summary)

		Column(modifier = modifier.fillMaxSize()) {
			Column(modifier = Modifier.padding(Ui.Padding.XL)) {
				TextField(
					value = newSceneItemNameText,
					onValueChange = { newSceneItemNameText = it },
					label = { Text("New Scene Name") }
				)
				Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
					ExtendedFloatingActionButton(
						onClick = {
							component.createScene(newSceneItemNameText)
							newSceneItemNameText = ""
						},
						text = { Text("Scene") },
						icon = { Icon(Icons.Filled.Add, "") },
						modifier = Modifier.padding(top = Ui.Padding.XL)
					)
					ExtendedFloatingActionButton(
						onClick = {
							component.createGroup(newSceneItemNameText)
							newSceneItemNameText = ""
						},
						text = { Text("Group") },
						icon = { Icon(Icons.Filled.Add, "") },
						modifier = Modifier.padding(top = Ui.Padding.XL)
					)
					Button(
						onClick = treeState::collapseAll,
						modifier = Modifier.padding(top = Ui.Padding.XL)
					) {
						Text("C")
					}
					Button(
						onClick = treeState::expandAll,
						modifier = Modifier.padding(top = Ui.Padding.XL)
					) {
						Text("E")
					}
				}
				Row(
					modifier = Modifier.fillMaxWidth()
						.wrapContentHeight()
						.padding(vertical = Ui.Padding.XL),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						"\uD83D\uDCDD Scenes:",
						style = MaterialTheme.typography.headlineSmall,
						color = MaterialTheme.colorScheme.onBackground
					)
				}
			}

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
				}
			)
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
		SceneItemUi(
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SceneItemUi(
	scene: SceneItem,
	draggable: Modifier,
	depth: Int,
	hasDirtyBuffer: Boolean,
	isSelected: Boolean,
	onSceneSelected: (SceneItem) -> Unit,
	onSceneAltClick: (SceneItem) -> Unit,
) {
	Surface(
		modifier = draggable
			.fillMaxWidth()
			.wrapContentHeight()
			.padding(start = (Ui.Padding.XL + (Ui.Padding.XL * (depth - 1) * 2)))
			.background(if (isSelected) selectionColor() else MaterialTheme.colorScheme.surfaceVariant)
			.combinedClickable(
				onClick = { onSceneSelected(scene) },
			),
		color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
		tonalElevation = if (isSelected) Ui.Elevation.MEDIUM else 0.dp,
		border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant) else null
	) {
		Row(
			modifier = Modifier
				.wrapContentHeight()
				.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				scene.name,
				style = MaterialTheme.typography.bodyLarge,
				modifier = Modifier.weight(1f).padding(start = Ui.Padding.L)
			)
			if (hasDirtyBuffer) {
				Unsaved()
			}
			IconButton(onClick = { onSceneAltClick(scene) }, modifier = Modifier) {
				Icon(
					imageVector = Icons.Filled.Delete,
					contentDescription = "Delete",
					modifier = Modifier.size(18.dp),
				)
			}
		}
	}
}

@Composable
fun SceneGroupItem(
	sceneNode: TreeValue<SceneItem>,
	draggable: Modifier,
	hasDirtyBuffer: Set<Int>,
	toggleExpand: (nodeId: Int) -> Unit,
	collapsed: Boolean,
	onSceneAltClick: (SceneItem) -> Unit,
) {
	val (scene: SceneItem, _, _, children: List<TreeValue<SceneItem>>) = sceneNode

	Surface(
		modifier = draggable
			.fillMaxWidth()
			.padding(
				start = (Ui.Padding.XL + (Ui.Padding.XL * (sceneNode.depth - 1) * 2)).coerceAtLeast(0.dp),
			)
			.clickable(onClick = { toggleExpand(sceneNode.value.id) }),
		tonalElevation = if (collapsed) Ui.Elevation.SMALL else 0.dp,
		border = if (collapsed) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
	) {
		Row(
			modifier = Modifier.padding(Ui.Padding.XL).fillMaxWidth(),
		) {
			if (collapsed) {
				Icon(
					imageVector = Icons.Filled.Folder,
					contentDescription = "Group Collapsed",
					modifier = Modifier.size(24.dp).padding(end = Ui.Padding.M),
				)
			} else {
				Icon(
					imageVector = Icons.Filled.FolderOpen,
					contentDescription = "Group Expanded",
					modifier = Modifier.size(24.dp).padding(end = Ui.Padding.M),
				)
			}

			Text(
				scene.name,
				modifier = Modifier.weight(1f),
				style = MaterialTheme.typography.bodyLarge
			)

			if (children.any { hasDirtyBuffer.contains(it.value.id) }) {
				Unsaved()
			}

			if (children.isEmpty()) {
				IconButton(onClick = { onSceneAltClick(scene) }, modifier = Modifier) {
					Icon(
						imageVector = Icons.Filled.Delete,
						contentDescription = "Delete",
						modifier = Modifier.size(18.dp),
					)
				}
			}
		}
	}
}

@Composable
private fun Unsaved() {
	Icon(
		imageVector = Icons.Outlined.Edit,
		contentDescription = "Unsaved",
		modifier = Modifier.size(24.dp),
	)
}

@Composable
private fun selectionColor(): Color = MaterialTheme.colorScheme.tertiaryContainer

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
		modifier = Modifier.width(300.dp).padding(Ui.Padding.XL)
	)
}