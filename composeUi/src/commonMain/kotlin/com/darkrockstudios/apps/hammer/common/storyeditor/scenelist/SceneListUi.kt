package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.jetbrains.subscribeAsState
import com.darkrockstudios.apps.hammer.MR
import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneList
import com.darkrockstudios.apps.hammer.common.compose.HeaderUi
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.moko.get
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.emptySceneSummary
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.SceneTree
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.rememberReorderableLazyListState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(
	ExperimentalMaterialApi::class,
	ExperimentalComposeApi::class,
	ExperimentalMaterial3Api::class,
	ExperimentalFoundationApi::class
)
@Composable
fun SceneListUi(
	component: SceneList,
	snackbarHostState: RootSnackbarHostState,
	modifier: Modifier = Modifier,
) {
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	val state by component.state.subscribeAsState()
	var sceneDefDeleteTarget by remember { mutableStateOf<SceneItem?>(null) }

	var showCreateGroupDialog by remember { mutableStateOf<SceneItem?>(null) }
	var showCreateSceneDialog by remember { mutableStateOf<SceneItem?>(null) }
	var expandOrCollapse by remember { mutableStateOf(false) }

	val treeState = rememberReorderableLazyListState(
		summary = emptySceneSummary(state.projectDef),
		moveItem = { scope.launch { component.moveScene(it) } }
	)
	state.sceneSummary?.let { summary ->
		treeState.updateSummary(summary)
	}

	BoxWithConstraints {
		Column(modifier = modifier.fillMaxSize()) {
			Row(
				modifier = Modifier.fillMaxWidth()
					.wrapContentHeight()
					.padding(start = Ui.Padding.L, end = Ui.Padding.L, top = Ui.Padding.L),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				HeaderUi(MR.strings.scene_list_header, "\uD83D\uDCDD")

				IconButton(onClick = component::showOutlineOverview) {
					Icon(
						Icons.Filled.ViewList,
						contentDescription = MR.strings.scene_list_outline_overview_button.get(),
						tint = MaterialTheme.colorScheme.onSurface,
					)
				}

				if (expandOrCollapse) {
					ElevatedButton(onClick = {
						treeState.expandAll()
						expandOrCollapse = false
					}) {
						Icon(Icons.Filled.ExpandMore, MR.strings.expand.get())
					}
				} else {
					ElevatedButton(onClick = {
						treeState.collapseAll()
						expandOrCollapse = true
					}) {
						Icon(Icons.Filled.ExpandLess, MR.strings.collapse.get())
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
				Icon(Icons.Filled.CreateNewFolder, MR.strings.scene_list_create_group_button.get())
			}
			FloatingActionButton(onClick = {
				showCreateSceneDialog = treeState.summary.sceneTree.root.value
			}) {
				Icon(Icons.Filled.PostAdd, MR.strings.scene_list_create_group_button.get())
			}
		}
	}

	CreateDialog(
		show = showCreateGroupDialog != null,
		title = MR.strings.scene_list_create_group_dialog_title.get(),
		textLabel = MR.strings.scene_list_create_group_dialog_message.get()
	) { groupName ->
		scope.launch {
			Napier.d { "Create dialog close" }
			if (groupName != null) {
				component.createGroup(showCreateGroupDialog, groupName)
			}
			withContext(mainDispatcher) {
				showCreateGroupDialog = null
			}
		}
	}

	CreateDialog(
		show = showCreateSceneDialog != null,
		title = MR.strings.scene_list_create_scene_dialog_title.get(),
		textLabel = MR.strings.scene_list_create_scene_dialog_message.get()
	) { sceneName ->
		scope.launch {
			Napier.d { "Create dialog close" }
			if (sceneName != null) {
				component.createScene(showCreateSceneDialog, sceneName)
			}
			withContext(mainDispatcher) {
				showCreateSceneDialog = null
			}
		}
	}

	sceneDefDeleteTarget?.let { scene ->
		val node = treeState.getTree().find { it.value.id == scene.id }
		if (scene.type == SceneItem.Type.Group && node?.children?.isEmpty() == false) {
			GroupDeleteNotAllowedDialog(scene) {
				sceneDefDeleteTarget = null
			}
		} else {
			SceneDeleteDialog(scene) { deleteScene ->
				scope.launch {
					if (deleteScene) {
						component.deleteScene(scene)
					}
					withContext(mainDispatcher) {
						sceneDefDeleteTarget = null
					}
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