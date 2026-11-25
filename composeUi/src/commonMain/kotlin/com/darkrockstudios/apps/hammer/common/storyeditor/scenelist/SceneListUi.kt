package com.darkrockstudios.apps.hammer.common.storyeditor.scenelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darkrockstudios.apps.hammer.common.compose.theme.LocalHammerColors
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.archived_scenes_restored_snackbar
import com.darkrockstudios.apps.hammer.collapse
import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneList
import com.darkrockstudios.apps.hammer.help_button
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import com.darkrockstudios.apps.hammer.common.compose.RootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCoachMarkAnchors
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdCoachMarkHost
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdHairlineButton
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdMonoLabel
import com.darkrockstudios.apps.hammer.common.compose.designsystem.HdScrollAwayFooter
import com.darkrockstudios.apps.hammer.common.compose.designsystem.hdCoachMarkAnchor
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberHdCoachMarkAnchors
import com.darkrockstudios.apps.hammer.common.compose.designsystem.rememberHdScrollAwayFooterState
import com.darkrockstudios.apps.hammer.common.compose.focus.LocalPaneFocus
import com.darkrockstudios.apps.hammer.common.compose.focus.PaneId
import com.darkrockstudios.apps.hammer.common.compose.focus.paneEntry
import com.darkrockstudios.apps.hammer.common.compose.rememberMainDispatcher
import com.darkrockstudios.apps.hammer.common.compose.rememberStrRes
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.compose.serializableSaver
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.emptySceneSummary
import com.darkrockstudios.apps.hammer.common.data.nux.NuxHint
import com.darkrockstudios.apps.hammer.common.data.tree.TreeValue
import com.darkrockstudios.apps.hammer.common.data.tree.isChapter
import com.darkrockstudios.apps.hammer.common.storyeditor.SceneKind
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneKind
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.SceneTree
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.SceneTreeState
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.scenetree.rememberReorderableLazyListState
import com.darkrockstudios.apps.hammer.common.util.StrRes
import com.darkrockstudios.apps.hammer.common.help.HELP_BUTTON_TAG
import com.darkrockstudios.apps.hammer.common.help.HelpDialog
import com.darkrockstudios.apps.hammer.common.help.HelpScreen
import com.darkrockstudios.apps.hammer.expand
import com.darkrockstudios.apps.hammer.more_menu_button
import com.darkrockstudios.apps.hammer.scene_archived_snackbar
import com.darkrockstudios.apps.hammer.scene_list_add_button
import com.darkrockstudios.apps.hammer.scene_list_count_format
import com.darkrockstudios.apps.hammer.scene_list_create_chapter_group_dialog_title
import com.darkrockstudios.apps.hammer.scene_list_create_chapter_scene_dialog_title
import com.darkrockstudios.apps.hammer.scene_list_create_group_dialog_title
import com.darkrockstudios.apps.hammer.scene_list_create_menu_chapter_group
import com.darkrockstudios.apps.hammer.scene_list_create_menu_chapter_scene
import com.darkrockstudios.apps.hammer.scene_list_create_scene_dialog_title
import com.darkrockstudios.apps.hammer.scene_list_header
import com.darkrockstudios.apps.hammer.scene_list_outline_button
import com.darkrockstudios.apps.hammer.scene_list_reader_button
import com.darkrockstudios.apps.hammer.scene_list_tip_context_menu_body
import com.darkrockstudios.apps.hammer.scene_list_tip_context_menu_title
import com.darkrockstudios.apps.hammer.scene_list_tip_dismiss
import com.darkrockstudios.apps.hammer.scene_list_tip_reorder_body
import com.darkrockstudios.apps.hammer.scene_list_tip_reorder_title
import com.darkrockstudios.apps.hammer.scene_list_tip_swipe_body
import com.darkrockstudios.apps.hammer.scene_list_tip_swipe_title
import com.darkrockstudios.apps.hammer.scene_list_tip_turn_off
import com.darkrockstudios.apps.hammer.scene_list_tip_turned_off
import com.darkrockstudios.apps.hammer.view_archived_scenes_button
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

const val SCENE_LIST_ADD_BUTTON_TAG = "scene-list-add"
const val SCENE_LIST_ADD_SCENE_TAG = "scene-list-add-scene"
const val SCENE_LIST_ADD_GROUP_TAG = "scene-list-add-group"
const val CREATE_ITEM_NAME_FIELD_TAG = "create-item-name-field"
const val SCENE_LIST_TREE_TAG = "scene-list-tree"
const val SCENE_LIST_READER_BUTTON_TAG = "scene-list-reader"
const val SCENE_LIST_TITLE_TAG = "scene-list-title"
const val SCENE_LIST_MENU_TAG = "scene-list-menu"

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
	var sceneDefMoveTarget by rememberSaveable(stateSaver = serializableSaver<SceneItem>()) {
		mutableStateOf(
			null
		)
	}

	var addMenuOpen by remember { mutableStateOf(false) }

	fun moveScene(request: MoveRequest) {
		scope.launch { component.moveScene(request) }
	}

	val treeState = rememberReorderableLazyListState(
		summary = state.sceneSummary ?: emptySceneSummary(state.projectDef),
		moveItem = ::moveScene
	)
	LaunchedEffect(state.sceneSummary) {
		state.sceneSummary?.let { summary ->
			treeState.updateSummary(summary)
		}
	}

	val revealRequests = LocalPaneFocus.current?.revealRequests(PaneId.List) ?: 0
	LaunchedEffect(revealRequests) {
		if (revealRequests > 0) {
			state.selectedSceneItem?.let { treeState.revealCursor(it.id) }
		}
	}

	val footerState = rememberHdScrollAwayFooterState()
	// Always reachable when the list can't scroll down to it (e.g. all groups collapsed).
	val footerVisible = !treeState.listState.canScrollForward || !footerState.isHiddenByScroll

	val coachMarkAnchors = rememberHdCoachMarkAnchors()
	val tipsTurnedOffMessage = Res.string.scene_list_tip_turned_off.get()
	val offeredHints = state.offeredHints
	val shownTip by remember(treeState, offeredHints, footerVisible) {
		derivedStateOf { anchorableTip(treeState, offeredHints, footerVisible) }
	}
	val shownHint = shownTip?.hint
	val hintRowId = shownTip?.rowId

	val surfaceColor = if (inSplitPane) {
		MaterialTheme.colorScheme.surfaceContainerLow
	} else {
		MaterialTheme.colorScheme.surface
	}

	Surface(
		modifier = modifier,
		color = surfaceColor,
	) {
		HdCoachMarkHost(
			anchors = coachMarkAnchors,
			activeKey = hintRowId,
			title = when (shownHint) {
				NuxHint.SceneListReorder -> Res.string.scene_list_tip_reorder_title.get()
				NuxHint.SceneListContextMenu -> Res.string.scene_list_tip_context_menu_title.get()
				else -> Res.string.scene_list_tip_swipe_title.get()
			},
			body = when (shownHint) {
				NuxHint.SceneListReorder -> Res.string.scene_list_tip_reorder_body.get()
				NuxHint.SceneListContextMenu -> Res.string.scene_list_tip_context_menu_body.get()
				else -> Res.string.scene_list_tip_swipe_body.get()
			},
			dismissLabel = Res.string.scene_list_tip_dismiss.get(),
			onDismiss = { shownHint?.let(component::dismissHint) },
			secondaryLabel = Res.string.scene_list_tip_turn_off.get(),
			onSecondary = {
				component.turnOffTips()
				snackbarHostState.showSnackbar(tipsTurnedOffMessage)
			},
			modifier = Modifier.fillMaxSize(),
		) {
			Column(modifier = Modifier.fillMaxSize()) {
				val sceneCount = remember(state.sceneSummary) { countScenes(state.sceneSummary) }
				val chapterCount = treeState.summary.sceneTree.root.children.size

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.height(Ui.TOP_BAR_HEIGHT)
						.padding(horizontal = Ui.Padding.XL),
					horizontalArrangement = Arrangement.spacedBy(Ui.Padding.S),
					verticalAlignment = Alignment.CenterVertically,
				) {
					HdMonoLabel(
						text = Res.string.scene_list_header.get(),
						maxLines = 1,
						softWrap = false,
						modifier = Modifier.testTag(SCENE_LIST_TITLE_TAG),
					)
					// Weighted, so in a narrow pane the count gives way before the menu does.
					HdMonoLabel(
						text = Res.string.scene_list_count_format.get(chapterCount, sceneCount),
						maxLines = 1,
						softWrap = false,
						overflow = TextOverflow.Ellipsis,
						textAlign = TextAlign.End,
						modifier = Modifier.weight(1f),
					)
					OverflowMenu(component, treeState)
				}

				HorizontalDivider(
					thickness = Dp.Hairline,
					color = MaterialTheme.colorScheme.outlineVariant,
				)

				Box(modifier = Modifier.weight(1f).clipToBounds()) {
					SceneTree(
						modifier = Modifier.fillMaxSize().testTag(SCENE_LIST_TREE_TAG)
							.nestedScroll(footerState.nestedScrollConnection)
							.paneEntry(PaneId.List),
						state = treeState,
						itemUi = { node: TreeValue<SceneItem>,
						           toggleExpanded: (nodeId: Int) -> Unit,
						           collapsed: Boolean,
						           draggable: Modifier,
						           cursor: Boolean ->

							SceneNode(
								sceneNode = node,
								draggableModifier = draggable,
								state = state,
								summary = treeState.summary,
								component = component,
								toggleExpand = toggleExpanded,
								collapsed = collapsed,
								isCursor = cursor,
								coachMarkAnchors = coachMarkAnchors,
								isHintRow = node.value.id == hintRowId,
								demonstrateSwipe = shownHint == NuxHint.SceneListSwipeMenu && node.value.id == hintRowId,
								isChapter = node.depth == 1,
								sceneDefDeleteTarget = { deleteTarget ->
									sceneDefDeleteTarget = deleteTarget
								},
								sceneDefRenameTarget = { renameTarget ->
									sceneDefRenameTarget = renameTarget
								},
								sceneDefArchiveTarget = { archiveTarget ->
									sceneDefArchiveTarget = archiveTarget
								},
								sceneDefMoveTarget = { moveTarget ->
									sceneDefMoveTarget = moveTarget
								},
								createScene = { parent -> component.beginCreate(SceneList.CreateRequest.Kind.Scene, parent) },
								createGroup = { parent -> component.beginCreate(SceneList.CreateRequest.Kind.Group, parent) },
							)
						},
						contentPadding = PaddingValues(bottom = footerState.height),
						onOpen = component::onSceneSelected,
						openSceneId = state.selectedSceneItem?.id,
					)

					HdScrollAwayFooter(
						state = footerState,
						visible = footerVisible,
						containerColor = surfaceColor,
					) {
						HdHairlineButton(
							label = Res.string.scene_list_outline_button.get(),
							onClick = component::showOutlineOverview,
							modifier = Modifier.weight(1f),
						)
						HdHairlineButton(
							label = Res.string.scene_list_reader_button.get(),
							onClick = component::showReaderMode,
							modifier = Modifier.weight(1f).testTag(SCENE_LIST_READER_BUTTON_TAG),
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
									text = { Text(Res.string.scene_list_create_menu_chapter_scene.get()) },
									leadingIcon = {
										Icon(
											imageVector = Icons.Outlined.Bookmark,
											contentDescription = null,
										)
									},
									onClick = {
										addMenuOpen = false
										component.beginCreate(SceneList.CreateRequest.Kind.Scene, null)
									},
								)
								DropdownMenuItem(
									modifier = Modifier.testTag(SCENE_LIST_ADD_GROUP_TAG),
									text = { Text(Res.string.scene_list_create_menu_chapter_group.get()) },
									leadingIcon = {
										Icon(
											imageVector = Icons.AutoMirrored.Outlined.MenuBook,
											contentDescription = null,
										)
									},
									onClick = {
										addMenuOpen = false
										component.beginCreate(SceneList.CreateRequest.Kind.Group, null)
									},
								)
							}
						}
					}
				}
			}
		}
	}

	// Children of the root are chapters, so the dialogs say so when that is the parent. The parent
	// is latched so the wording holds while the dialog animates closed.
	val createRequest = state.createRequest
	val createParent = createRequest?.parent
	var lastCreateParent by remember { mutableStateOf(createParent) }
	if (createParent != null) lastCreateParent = createParent
	val creatingChapter = lastCreateParent?.isRootScene == true

	CreateDialog(
		show = createRequest?.kind == SceneList.CreateRequest.Kind.Group,
		title = if (creatingChapter) {
			Res.string.scene_list_create_chapter_group_dialog_title.get()
		} else {
			Res.string.scene_list_create_group_dialog_title.get()
		},
		parentName = lastCreateParent?.takeUnless { creatingChapter }?.name,
	) { groupName ->
		Napier.d { "Create dialog close" }
		val parent = component.state.value.createRequest?.parent
		if (groupName != null && parent != null) {
			scope.launch { component.createGroup(parent, groupName) }
		} else {
			component.dismissCreate()
		}
	}

	CreateDialog(
		show = createRequest?.kind == SceneList.CreateRequest.Kind.Scene,
		title = if (creatingChapter) {
			Res.string.scene_list_create_chapter_scene_dialog_title.get()
		} else {
			Res.string.scene_list_create_scene_dialog_title.get()
		},
		parentName = lastCreateParent?.takeUnless { creatingChapter }?.name,
	) { sceneName ->
		Napier.d { "Create dialog close" }
		val parent = component.state.value.createRequest?.parent
		if (sceneName != null && parent != null) {
			scope.launch { component.createScene(parent, sceneName) }
		} else {
			component.dismissCreate()
		}
	}

	sceneDefDeleteTarget?.let { scene ->
		DeleteSceneDialog(treeState, scene, scope, component, mainDispatcher) {
			sceneDefDeleteTarget = null
		}
	}

	sceneDefRenameTarget?.let { scene ->
		val kind = sceneKind(scene, treeState.summary.sceneTree.isChapter(scene))
		RenameSceneDialog(scene, kind, scope, component, mainDispatcher) {
			sceneDefRenameTarget = null
		}
	}

	sceneDefArchiveTarget?.let { scene ->
		ArchiveSceneDialog(scene, scope, component, mainDispatcher, snackbarHostState, strRes) {
			sceneDefArchiveTarget = null
		}
	}

	sceneDefMoveTarget?.let { scene ->
		MoveSceneDialog(
			item = scene,
			tree = treeState.summary.sceneTree,
			onDismiss = { sceneDefMoveTarget = null },
			onMove = ::moveScene,
		)
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

private fun countScenes(summary: SceneSummary?): Int {
	if (summary == null) return 0
	return (0 until summary.sceneTree.totalNodes).count { summary.sceneTree[it].value.type == SceneItem.Type.Scene }
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
	val isChapter = treeState.summary.sceneTree.isChapter(scene)
	if (scene.type == SceneItem.Type.Group && node?.children?.isEmpty() == false) {
		GroupDeleteNotAllowedDialog(scene, isChapter) {
			onDismiss()
		}
	} else {
		SceneDeleteDialog(scene, sceneKind(scene, isChapter)) { deleteScene ->
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
	kind: SceneKind,
	scope: CoroutineScope,
	component: SceneList,
	mainDispatcher: CoroutineContext,
	onDismiss: () -> Unit
) {
	SceneRenameDialog(scene, kind) { newName ->
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
	var helpOpen by rememberSaveable { mutableStateOf(false) }
	Box {
		IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(SCENE_LIST_MENU_TAG)) {
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

			DropdownMenuItem(
				modifier = Modifier.testTag(HELP_BUTTON_TAG),
				text = { Text(Res.string.help_button.get()) },
				leadingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, null) },
				onClick = {
					menuOpen = false
					helpOpen = true
				}
			)
		}
	}
	if (helpOpen) HelpDialog(screen = HelpScreen.Scenes, onDismiss = { helpOpen = false })
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
	isCursor: Boolean,
	coachMarkAnchors: HdCoachMarkAnchors,
	isHintRow: Boolean,
	demonstrateSwipe: Boolean,
	isChapter: Boolean,
	sceneDefDeleteTarget: (SceneItem) -> Unit,
	sceneDefRenameTarget: (SceneItem) -> Unit,
	sceneDefArchiveTarget: (SceneItem) -> Unit,
	sceneDefMoveTarget: (SceneItem) -> Unit,
	createScene: (SceneItem) -> Unit,
	createGroup: (SceneItem) -> Unit,
) {
	val scene = sceneNode.value
	val isSelected = scene == state.selectedSceneItem
	// Anchored on the action container, so the highlight holds still while the row slides.
	val anchor = if (isHintRow) Modifier.hdCoachMarkAnchor(coachMarkAnchors, scene.id) else Modifier
	if (scene.type == SceneItem.Type.Scene) {
		SceneItem(
			scene = scene,
			draggable = draggableModifier,
			depth = sceneNode.depth,
			hasDirtyBuffer = summary.hasDirtyBuffer.contains(scene.id),
			isSelected = isSelected,
			isCursor = isCursor,
			demonstrateSwipe = demonstrateSwipe,
			isChapter = isChapter,
			onSceneSelected = component::onSceneSelected,
			onSceneDeleteRequest = sceneDefDeleteTarget,
			onSceneRenameRequest = sceneDefRenameTarget,
			onSceneArchiveRequest = sceneDefArchiveTarget,
			onSceneMoveRequest = sceneDefMoveTarget,
			modifier = anchor,
		)
	} else {
		SceneGroupItem(
			sceneNode = sceneNode,
			draggable = draggableModifier,
			hasDirtyBuffer = summary.hasDirtyBuffer,
			toggleExpand = toggleExpand,
			collapsed = collapsed,
			isCursor = isCursor,
			demonstrateSwipe = demonstrateSwipe,
			isChapter = isChapter,
			onSceneDeleteRequest = sceneDefDeleteTarget,
			onSceneRenameRequest = sceneDefRenameTarget,
			onSceneMoveRequest = sceneDefMoveTarget,
			onCreateGroupClick = createGroup,
			onCreateSceneClick = createScene,
			modifier = anchor,
		)
	}
}

/** Trailing mark on a row with unsaved edits. */
@Composable
internal fun UnsavedMark() {
	Box(
		modifier = Modifier
			.padding(start = Ui.Padding.M)
			.size(6.dp)
			.background(LocalHammerColors.current.danger)
	)
}

private data class ShownTip(val hint: NuxHint, val rowId: Int)

/**
 * The first of [offered] that has a row to point at right now, and that row. A tip points at the
 * first row wholly in view, and a menu tip at the first such scene, whose menu it describes.
 */
private fun anchorableTip(
	treeState: SceneTreeState,
	offered: List<NuxHint>,
	footerVisible: Boolean,
): ShownTip? {
	val layout = treeState.listState.layoutInfo
	// The end padding sits under the footer, so it only hides rows while the footer is up.
	val visibleEnd = layout.viewportEndOffset - if (footerVisible) layout.afterContentPadding else 0
	val inView = layout.visibleItemsInfo.filter { it.offset >= 0 && it.offset + it.size <= visibleEnd }
	return offered.firstNotNullOfOrNull { hint ->
		val row = when (hint) {
			NuxHint.SceneListSwipeMenu, NuxHint.SceneListContextMenu ->
				inView.firstOrNull { it.contentType == SceneItem.Type.Scene }

			NuxHint.SceneListReorder -> inView.firstOrNull()
		}
		(row?.key as? Int)?.let { ShownTip(hint, it) }
	}
}
