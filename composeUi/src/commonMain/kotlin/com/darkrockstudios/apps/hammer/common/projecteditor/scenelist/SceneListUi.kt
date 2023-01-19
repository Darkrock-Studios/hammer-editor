package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
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

		Column(modifier = modifier.fillMaxWidth().padding(Ui.PADDING)) {
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
					modifier = Modifier.padding(top = Ui.PADDING)
				)
				ExtendedFloatingActionButton(
					onClick = {
						component.createGroup(newSceneItemNameText)
						newSceneItemNameText = ""
					},
					text = { Text("Group") },
					icon = { Icon(Icons.Filled.Add, "") },
					modifier = Modifier.padding(top = Ui.PADDING)
				)
				Button(
					onClick = treeState::collapseAll,
					modifier = Modifier.padding(top = Ui.PADDING)
				) {
					Text("C")
				}
				Button(
					onClick = treeState::expandAll,
					modifier = Modifier.padding(top = Ui.PADDING)
				) {
					Text("E")
				}
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
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.onBackground
				)
			}

			SceneTree(
				modifier = Modifier.fillMaxSize(),
				state = treeState
			) { sceneNode, toggleExpand, draggableModifier ->
				SceneNode(
					sceneNode = sceneNode,
					draggableModifier = draggableModifier,
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
	draggableModifier: Modifier,
	state: SceneList.State,
	summary: SceneSummary,
	component: SceneList,
	toggleExpand: (nodeId: Int) -> Unit,
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
			onSceneAltClick = { selectedScene ->
				sceneDefDeleteTarget(selectedScene)
			},
		)
	}
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
		colors = CardDefaults.cardColors(
			containerColor = if (isSelected) selectionColor() else MaterialTheme.colorScheme.surfaceVariant
		),
		elevation = if (isSelected) CardDefaults.elevatedCardElevation() else CardDefaults.cardElevation()
	) {
		Row(
			modifier = Modifier
				.padding(Ui.PADDING)
				.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Text(
				"Scene: ${scene.id} - ${scene.name}",
				style = MaterialTheme.typography.bodyMedium
			)
			if (hasDirtyBuffer) {
				Text(
					"Unsaved",
					style = MaterialTheme.typography.titleSmall
				)
			}
			Button({ onSceneAltClick(scene) }) {
				Text("X")
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneGroupItem(
	sceneNode: TreeValue<SceneItem>,
	draggable: Modifier,
	hasDirtyBuffer: Set<Int>,
	toggleExpand: (nodeId: Int) -> Unit,
	onSceneAltClick: (SceneItem) -> Unit,
) {
	val (scene: SceneItem, _, _, children: List<TreeValue<SceneItem>>) = sceneNode

	Card(
		modifier = draggable
			.fillMaxWidth()
			.padding(
				start = (Ui.PADDING + (Ui.PADDING * (sceneNode.depth - 1) * 2)).coerceAtLeast(0.dp),
				top = Ui.PADDING,
				bottom = Ui.PADDING,
				end = Ui.PADDING
			)
			.clickable(onClick = { toggleExpand(sceneNode.value.id) }),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
	) {
		Row(
			modifier = Modifier.padding(Ui.PADDING).fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			Text(
				"Group: ${scene.name}",
				style = MaterialTheme.typography.bodyMedium
			)

			if (children.any { hasDirtyBuffer.contains(it.value.id) }) {
				Text(
					"Unsaved",
					style = MaterialTheme.typography.titleSmall
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
		modifier = Modifier.width(300.dp).padding(Ui.PADDING)
	)
}