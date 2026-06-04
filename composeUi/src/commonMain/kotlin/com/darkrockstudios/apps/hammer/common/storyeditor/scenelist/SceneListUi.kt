package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.animation.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.*
import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneList
import com.darkrockstudios.apps.hammer.common.compose.*
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.emptySceneSummary
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.SceneTree
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.SceneTreeState
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.rememberReorderableLazyListState
import com.darkrockstudios.apps.hammer.common.util.StrRes
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

const val SCENE_LIST_ADD_BUTTON_TAG = "scene-list-add"
const val SCENE_LIST_ADD_SCENE_TAG = "scene-list-add-scene"
const val SCENE_LIST_ADD_GROUP_TAG = "scene-list-add-group"
const val CREATE_ITEM_NAME_FIELD_TAG = "create-item-name-field"

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
	inSplitPane: Boolean = false,
) {
	val scope = rememberCoroutineScope()
	val mainDispatcher = rememberMainDispatcher()
	val strRes = rememberStrRes()
	val state by component.state.subscribeAsState()
	var sceneDefDeleteTarget by rememberSaveable(stateSaver = serializableSaver<SceneItem>()) { mutableStateOf(null) }
	var sceneDefRenameTarget by rememberSaveable(stateSaver = serializableSaver<SceneItem>()) { mutableStateOf(null) }
	var sceneDefArchiveTarget by rememberSaveable(stateSaver = serializableSaver<SceneItem>()) { mutableStateOf(null) }

	var showCreateGroupDialog by rememberSaveable(stateSaver = serializableSaver<SceneItem>()) { mutableStateOf(null) }
	var showCreateSceneDialog by rememberSaveable(stateSaver = serializableSaver<SceneItem>()) { mutableStateOf(null) }

	var addMenuOpen by remember { mutableStateOf(false) }

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

	var footerVisible by remember { mutableStateOf(true) }
	val scrollConnection = remember {
		object : NestedScrollConnection {
			override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
				if (available.y < -1f) footerVisible = false
				else if (available.y > 1f) footerVisible = true
				return Offset.Zero
			}
		}
	}

	Surface(
		modifier = modifier,
		color = if (inSplitPane) {
			MaterialTheme.colorScheme.surfaceContainerLow
		} else {
			MaterialTheme.colorScheme.surface
		},
	) {
		Column(modifier = Modifier.fillMaxSize()) {
			val (sceneCount, groupCount) = remember(state.sceneSummary) {
				countSceneAndGroupNodes(state.sceneSummary)
			}

			Row(
				modifier = Modifier
					.fillMaxWidth()
					.height(Ui.TOP_BAR_HEIGHT)
					.padding(horizontal = Ui.Padding.XL),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				HdMonoLabel(text = Res.string.scene_list_header.get())
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
				) {
					HdMonoLabel(text = Res.string.scene_list_count_format.get(sceneCount, groupCount))
					OverflowMenu(component, treeState)
				}
			}

			HorizontalDivider(
				thickness = Dp.Hairline,
				color = MaterialTheme.colorScheme.outlineVariant,
			)

			Box(modifier = Modifier.weight(1f)) {
				SceneTree(
					modifier = Modifier.fillMaxSize().nestedScroll(scrollConnection),
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
							sceneDefArchiveTarget = { archiveTarget ->
								sceneDefArchiveTarget = archiveTarget
							},
							createScene = { parent -> showCreateSceneDialog = parent },
							createGroup = { parent -> showCreateGroupDialog = parent },
						)
					},
					contentPadding = PaddingValues(bottom = 80.dp)
				)
			}

			AnimatedVisibility(
				visible = footerVisible,
				enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
				exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
			) {
				Column {
					HorizontalDivider(
						thickness = Dp.Hairline,
						color = MaterialTheme.colorScheme.outlineVariant,
					)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = Ui.Padding.M, vertical = Ui.Padding.M),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(Ui.Padding.M),
					) {
						HdHairlineButton(
							label = Res.string.scene_list_outline_overview_button.get(),
							onClick = component::showOutlineOverview,
							modifier = Modifier.weight(1f),
						)
						Box {
							IconButton(
								onClick = { addMenuOpen = true },
								modifier = Modifier.testTag(SCENE_LIST_ADD_BUTTON_TAG),
							) {
								Icon(
									imageVector = Icons.Filled.Add,
									contentDescription = Res.string.scene_list_add_button.get(),
									tint = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
							DropdownMenu(
								expanded = addMenuOpen,
								onDismissRequest = { addMenuOpen = false },
							) {
								DropdownMenuItem(
									modifier = Modifier.testTag(SCENE_LIST_ADD_SCENE_TAG),
									text = { Text(Res.string.scene_list_create_menu_scene.get()) },
									leadingIcon = {
										Icon(
											imageVector = Icons.Filled.PostAdd,
											contentDescription = null,
										)
									},
									onClick = {
										addMenuOpen = false
										showCreateSceneDialog = treeState.summary.sceneTree.root.value
									},
								)
								DropdownMenuItem(
									modifier = Modifier.testTag(SCENE_LIST_ADD_GROUP_TAG),
									text = { Text(Res.string.scene_list_create_menu_group.get()) },
									leadingIcon = {
										Icon(
											imageVector = Icons.Filled.CreateNewFolder,
											contentDescription = null,
										)
									},
									onClick = {
										addMenuOpen = false
										showCreateGroupDialog = treeState.summary.sceneTree.root.value
									},
								)
							}
						}
					}
				}
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

	sceneDefArchiveTarget?.let { scene ->
		ArchiveSceneDialog(scene, scope, component, mainDispatcher, snackbarHostState, strRes) {
			sceneDefArchiveTarget = null
		}
	}

	if (state.showArchivedDialog) {
		ArchivedScenesDialog(
			archivedScenes = state.archivedScenes,
			onUnarchive = { scene ->
				scope.launch {
					component.unarchiveScene(scene)
					val message = strRes.get(Res.string.archived_scenes_restored_snackbar, scene.name)
					snackbarHostState.showSnackbar(message)
				}
			},
			onDismiss = { component.dismissArchivedDialog() }
		)
	}
}

private fun countSceneAndGroupNodes(summary: SceneSummary?): Pair<Int, Int> {
	if (summary == null) return 0 to 0
	var scenes = 0
	var groups = 0
	for (i in 0 until summary.sceneTree.totalNodes) {
		when (summary.sceneTree[i].value.type) {
			SceneItem.Type.Scene -> scenes++
			SceneItem.Type.Group -> groups++
			SceneItem.Type.Root -> Unit
		}
	}
	return scenes to groups
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

@OptIn(ExperimentalMaterialApi::class, ExperimentalComposeApi::class)
@Composable
private fun ArchiveSceneDialog(
	scene: SceneItem,
	scope: CoroutineScope,
	component: SceneList,
	mainDispatcher: CoroutineContext,
	snackbarHostState: RootSnackbarHostState,
	strRes: StrRes,
	onDismiss: () -> Unit
) {
	SceneArchiveDialog(scene) { doArchive ->
		scope.launch {
			if (doArchive) {
				component.archiveScene(scene)
				val message = strRes.get(Res.string.scene_archived_snackbar, scene.name)
				snackbarHostState.showSnackbar(message)
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
				tint = MaterialTheme.colorScheme.onSurfaceVariant
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
				text = { Text(Res.string.view_archived_scenes_button.get()) },
				leadingIcon = { Icon(Icons.Default.Archive, Res.string.view_archived_scenes_button.get()) },
				onClick = {
					menuOpen = false
					component.showArchivedScenes()
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
	sceneDefArchiveTarget: (SceneItem) -> Unit,
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
			onSceneArchiveRequest = sceneDefArchiveTarget,
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
