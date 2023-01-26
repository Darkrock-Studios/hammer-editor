package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.emptySceneSummary
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

	var showCreateGroupDialog by remember { mutableStateOf<SceneItem?>(null) }
	var showCreateSceneDialog by remember { mutableStateOf<SceneItem?>(null) }
	var expandOrCollapse by remember { mutableStateOf(false) }

	val treeState = rememberReorderableLazyListState(
		summary = emptySceneSummary(state.projectDef),
		moveItem = component::moveScene
	)
	state.sceneSummary?.let { summary ->
		treeState.updateSummary(summary)
	}

	BoxWithConstraints {
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
						summary = treeState.summary,
						component = component,
						toggleExpand = toggleExpanded,
						collapsed = collapsed,
						sceneDefDeleteTarget = { deleteTarget ->
							sceneDefDeleteTarget = deleteTarget
						},
						createScene = { parent -> showCreateSceneDialog = parent },
						createGroup = { parent -> showCreateGroupDialog = parent }
					)
				},
				contentPadding = PaddingValues(bottom = 100.dp)
			)
		}

		Row(modifier = Modifier.padding(Ui.Padding.L).align(Alignment.BottomEnd)) {
			FloatingActionButton(
				onClick = { showCreateGroupDialog = treeState.summary.sceneTree.root.value },
				modifier = Modifier.padding(end = Ui.Padding.M)
			) {
				Icon(Icons.Filled.CreateNewFolder, "Create Group")
			}
			FloatingActionButton(onClick = {
				showCreateSceneDialog = treeState.summary.sceneTree.root.value
			}) {
				Icon(Icons.Filled.PostAdd, "Create Scene")
			}
		}
	}

	CreateDialog(
		show = showCreateGroupDialog != null,
		title = "Create Group",
		textLabel = "Group Name"
	) { groupName ->
		Napier.d { "Create dialog close" }
		if (groupName != null) {
			component.createGroup(showCreateGroupDialog, groupName)
		}
		showCreateGroupDialog = null
	}

	CreateDialog(
		show = showCreateSceneDialog != null,
		title = "Create Scene",
		textLabel = "Scene Name"
	) { sceneName ->
		Napier.d { "Create dialog close" }
		if (sceneName != null) {
			component.createScene(showCreateSceneDialog, sceneName)
		}
		showCreateSceneDialog = null
	}

	sceneDefDeleteTarget?.let { scene ->
		val node = treeState.getTree().find { it.value.id == scene.id }
		if (scene.type == SceneItem.Type.Group && node?.children?.isEmpty() == false) {
			GroupDeleteNotAllowedDialog(scene) {
				sceneDefDeleteTarget = null
			}
		} else {
			SceneDeleteDialog(scene) { deleteScene ->
				if (deleteScene) {
					component.deleteScene(scene)
				}
				sceneDefDeleteTarget = null
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
	createScene: (SceneItem) -> Unit,
	createGroup: (SceneItem) -> Unit
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
			onSceneAltClick = sceneDefDeleteTarget,
		)
	} else {
		SceneGroupItem(
			sceneNode = sceneNode,
			draggable = draggableModifier,
			hasDirtyBuffer = summary.hasDirtyBuffer,
			toggleExpand = toggleExpand,
			collapsed = collapsed,
			onSceneAltClick = sceneDefDeleteTarget,
			onCreateGroupClick = createGroup,
			onCreateSceneClick = createScene
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