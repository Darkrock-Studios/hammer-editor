package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneList
import com.darkrockstudios.apps.hammer.common.compose.HeaderUi
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.emptySceneSummary
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.SceneTree
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.SceneTreeState
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.rememberReorderableLazyListState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

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
	var sceneDefRenameTarget by remember { mutableStateOf<SceneItem?>(null) }

	var showCreateGroupDialog by remember { mutableStateOf<SceneItem?>(null) }
	var showCreateSceneDialog by remember { mutableStateOf<SceneItem?>(null) }

	val treeState = rememberReorderableLazyListState(
		summary = state.sceneSummary ?: emptySceneSummary(state.projectDef),
		moveItem = { scope.launch { component.moveScene(it) } }
	)
	LaunchedEffect(state.sceneSummary) {
		state.sceneSummary?.let { summary ->
			treeState.updateSummary(summary)
		}
	}

	// 1 in 10 chance of doing NUX
	// TODO implement a real NUX system
	val shouldNux = remember { Random.nextInt(0, 9) == 0 }

	Box {
		Column(modifier = modifier.fillMaxSize()) {
			Row(
				modifier = Modifier.fillMaxWidth().height(Ui.TOP_BAR_HEIGHT).padding(horizontal = Ui.Padding.XL),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				HeaderUi(Res.string.scene_list_header, "\uD83D\uDCDD")

				OverflowMenu(component, treeState)
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
						shouldNux = shouldNux,
						sceneDefDeleteTarget = { deleteTarget ->
							sceneDefDeleteTarget = deleteTarget
						},
						sceneDefRenameTarget = { renameTarget ->
							sceneDefRenameTarget = renameTarget
						},
						createScene = { parent -> showCreateSceneDialog = parent },
						createGroup = { parent -> showCreateGroupDialog = parent },
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
				Icon(Icons.Filled.CreateNewFolder, Res.string.scene_list_create_group_button.get())
			}
			FloatingActionButton(onClick = {
				showCreateSceneDialog = treeState.summary.sceneTree.root.value
			}) {
				Icon(Icons.Filled.PostAdd, Res.string.scene_list_create_group_button.get())
			}
		}
	}

	CreateDialog(
		show = showCreateGroupDialog != null,
		title = Res.string.scene_list_create_group_dialog_title.get(),
		textLabel = Res.string.scene_list_create_group_dialog_message.get()
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
		title = Res.string.scene_list_create_scene_dialog_title.get(),
		textLabel = Res.string.scene_list_create_scene_dialog_message.get()
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
		DeleteSceneDialog(treeState, scene, scope, component, mainDispatcher) {
			sceneDefDeleteTarget = null
		}
	}

	sceneDefRenameTarget?.let { scene ->
		RenameSceneDialog(scene, scope, component, mainDispatcher) {
			sceneDefRenameTarget = null
		}
	}
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun DeleteSceneDialog(
	treeState: SceneTreeState,
	scene: SceneItem,
	scope: CoroutineScope,
	component: SceneList,
	mainDispatcher: CoroutineContext,
	onDismiss: () -> Unit
) {
	val node = treeState.getTree().find { it.value.id == scene.id }
	if (scene.type == SceneItem.Type.Group && node?.children?.isEmpty() == false) {
		GroupDeleteNotAllowedDialog(scene) {
			onDismiss()
		}
	} else {
		SceneDeleteDialog(scene) { deleteScene ->
			scope.launch {
				if (deleteScene) {
					component.deleteScene(scene)
				}
				withContext(mainDispatcher) {
					onDismiss()
				}
			}
		}
	}
}

@Composable
private fun RenameSceneDialog(
	scene: SceneItem,
	scope: CoroutineScope,
	component: SceneList,
	mainDispatcher: CoroutineContext,
	onDismiss: () -> Unit
) {
	SceneRenameDialog(scene) { newName ->
		scope.launch {
			if (newName != null) {
				component.renameScene(scene, newName)
			}
			withContext(mainDispatcher) {
				onDismiss()
			}
		}
	}
}

@Composable
private fun OverflowMenu(component: SceneList, treeState: SceneTreeState) {
	var expandOrCollapse by rememberSaveable { mutableStateOf(false) }
	var menuOpen by rememberSaveable { mutableStateOf(false) }
	Box {
		IconButton(onClick = { menuOpen = true }) {
			Icon(
				Icons.Filled.MoreVert,
				contentDescription = Res.string.more_menu_button.get(),
				tint = MaterialTheme.colorScheme.onSurface
			)
		}

		DropdownMenu(
			expanded = menuOpen,
			onDismissRequest = { menuOpen = false },
		) {
			if (expandOrCollapse) {
				DropdownMenuItem(
					text = { Text(Res.string.expand.get()) },
					leadingIcon = { Icon(Icons.Default.ExpandMore, Res.string.expand.get()) },
					onClick = {
						menuOpen = false
						expandOrCollapse = false
						treeState.expandAll()
					}
				)
			} else {
				DropdownMenuItem(
					text = { Text(Res.string.collapse.get()) },
					leadingIcon = { Icon(Icons.Default.ExpandLess, Res.string.expand.get()) },
					onClick = {
						menuOpen = false
						expandOrCollapse = true
						treeState.collapseAll()
					}
				)
			}

			DropdownMenuItem(
				text = { Text(Res.string.scene_list_outline_overview_button.get()) },
				leadingIcon = { Icon(Icons.Default.ViewList, Res.string.scene_list_outline_overview_button.get()) },
				onClick = {
					menuOpen = false
					component.showOutlineOverview()
				}
			)
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
	shouldNux: Boolean,
	sceneDefDeleteTarget: (SceneItem) -> Unit,
	sceneDefRenameTarget: (SceneItem) -> Unit,
	createScene: (SceneItem) -> Unit,
	createGroup: (SceneItem) -> Unit,
) {
	val doNux = (shouldNux && sceneNode.index == 1)

	val scene = sceneNode.value
	val isSelected = scene == state.selectedSceneItem
	if (scene.type == SceneItem.Type.Scene) {
		SceneItem(
			scene = scene,
			draggable = draggableModifier,
			depth = sceneNode.depth,
			hasDirtyBuffer = summary.hasDirtyBuffer.contains(scene.id),
			isSelected = isSelected,
			shouldNux = doNux,
			onSceneSelected = component::onSceneSelected,
			onSceneDeleteRequest = sceneDefDeleteTarget,
			onSceneRenameRequest = sceneDefRenameTarget,
		)
	} else {
		SceneGroupItem(
			sceneNode = sceneNode,
			draggable = draggableModifier,
			hasDirtyBuffer = summary.hasDirtyBuffer,
			toggleExpand = toggleExpand,
			collapsed = collapsed,
			shouldNux = doNux,
			onSceneDeleteRequest = sceneDefDeleteTarget,
			onSceneRenameRequest = sceneDefRenameTarget,
			onCreateGroupClick = createGroup,
			onCreateSceneClick = createScene
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.Unsaved(hasDirtyBuffer: Boolean) {
	if (hasDirtyBuffer) {
		Badge(modifier = Modifier.align(Alignment.TopEnd).padding(Ui.Padding.M))
	}
}

@Composable
internal fun selectionColor(): Color = MaterialTheme.colorScheme.tertiaryContainer
