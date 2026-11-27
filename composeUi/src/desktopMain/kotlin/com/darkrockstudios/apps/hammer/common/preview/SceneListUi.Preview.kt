package com.darkrockstudios.apps.hammer.common.preview

import com.darkrockstudios.apps.hammer.common.storyeditor.EmptySceneEditor
import com.darkrockstudios.apps.hammer.common.compose.rightBorder
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.compose.resources.get
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.CreateDialogCard
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneRenameCard
import com.darkrockstudios.apps.hammer.common.storyeditor.SceneKind
import com.darkrockstudios.apps.hammer.scene_list_create_scene_dialog_title
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.Padded
import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneList
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.compose.theme.AppTheme
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.nux.NuxHint
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneListUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf


@Preview
@Composable
fun ScreenSceneListUiPreview() {
	val snackbarHostState = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		val component = fakeComponent(
			SceneList.State(
				projectDef = fakeProjectDef(),
				sceneSummary = fakeSceneSummary()
			)
		)
		SceneListUi(component, snackbarHostState)
	}
}

@Preview(widthDp = TABLET_WIDTH_DP, heightDp = TABLET_HEIGHT_DP)
@Composable
fun ScreenSceneListUiTabletPreview() {
	val snackbarHostState = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		TabletPreviewSurface {
			val summary = nestedSceneSummary()
			val component = fakeComponent(
				SceneList.State(
					projectDef = fakeProjectDef(),
					sceneSummary = summary,
					selectedSceneItem = summary.sceneTree.find { it.value.name == SELECTED_SCENE }?.value,
				)
			)
			SceneListUi(component, snackbarHostState)
		}
	}
}

/** The scene list showing [hint]. */
@Composable
fun ScreenSceneListUiTipPreview(hint: NuxHint) {
	val snackbarHostState = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		TabletPreviewSurface {
			val component = fakeComponent(
				SceneList.State(
					projectDef = fakeProjectDef(),
					sceneSummary = nestedSceneSummary(),
					offeredHints = persistentListOf(hint),
				)
			)
			SceneListUi(component, snackbarHostState)
		}
	}
}

/** The story split view before a scene is opened: the list, and beside it on wide screens the empty editor. */
@Composable
fun ScreenStoryNoSceneTabletPreview() {
	val snackbarHostState = rememberRootSnackbarHostState()
	KoinApplicationPreview {
		TabletPreviewSurface {
			val component = fakeComponent(SceneList.State(projectDef = fakeProjectDef(), sceneSummary = fakeSceneSummary()))
			BoxWithConstraints(Modifier.fillMaxSize()) {
				if (maxWidth < 600.dp) {
					SceneListUi(component, snackbarHostState)
				} else {
					Row(Modifier.fillMaxSize()) {
						Box(
							Modifier.width(300.dp).fillMaxHeight()
								.rightBorder(Dp.Hairline, MaterialTheme.colorScheme.outlineVariant),
						) {
							SceneListUi(component, snackbarHostState, inSplitPane = true)
						}
						Box(Modifier.weight(1f).fillMaxHeight()) { EmptySceneEditor(isMultipane = true) }
					}
				}
			}
		}
	}
}

@Preview(widthDp = 320, heightDp = 780)
@Composable
fun ScreenSceneListUiNestedPreview() = NestedSceneList(useDarkTheme = true)

@Preview(widthDp = 320, heightDp = 780)
@Composable
fun ScreenSceneListUiNestedLightPreview() = NestedSceneList(useDarkTheme = false)

@Composable
private fun NestedSceneList(useDarkTheme: Boolean) {
	val snackbarHostState = rememberRootSnackbarHostState()

	KoinApplicationPreview {
		AppTheme(globalSettingsPreview, useDarkTheme) {
			val summary = nestedSceneSummary()
			val component = fakeComponent(
				SceneList.State(
					projectDef = fakeProjectDef(),
					sceneSummary = summary,
					selectedSceneItem = summary.sceneTree.find { it.value.name == SELECTED_SCENE }?.value,
				)
			)
			SceneListUi(component, snackbarHostState)
		}
	}
}

private const val SELECTED_SCENE = "Down the hole"
private const val DIRTY_SCENE = "White Rabbit Arrives"

private fun nestedSceneSummary(): SceneSummary {
	var nextId = 1
	fun scene(name: String) = TreeNode(namedScene(nextId++, name, SceneItem.Type.Scene))
	fun group(name: String) = TreeNode(namedScene(nextId++, name, SceneItem.Type.Group))

	val tree = Tree<SceneItem>()
	val root = TreeNode(namedScene(0, "Root", SceneItem.Type.Root))

	root.addChild(scene("Title"))
	root.addChild(
		group("Chapter I").apply {
			addChild(scene("At Home"))
		}
	)
	root.addChild(scene("A Caucus Race and a Long Tale"))
	root.addChild(
		group("Chapter IV").apply {
			addChild(scene("Mushroom Consumed"))
			addChild(
				group("Underground").apply {
					addChild(scene("Down the hole"))
					addChild(
						group("A sea of tears").apply {
							addChild(scene("The Pool of Tears"))
							addChild(scene("White Rabbit Arrives"))
						}
					)
				}
			)
			addChild(scene("The Rabbit Sends in a Little Bill"))
			addChild(scene("Bottom of Chimney"))
		}
	)
	root.addChild(
		group("Chapter VI").apply {
			addChild(
				group("The Duchess").apply {
					addChild(scene("Pig and Pepper"))
				}
			)
		}
	)
	root.addChild(group("Chapter VII"))

	tree.setRoot(root)

	val immutable = tree.toImmutableTree()
	val dirtyId = immutable.first { it.value.name == DIRTY_SCENE }.value.id

	return SceneSummary(
		immutable,
		persistentSetOf(dirtyId),
	)
}

private fun namedScene(id: Int, name: String, type: SceneItem.Type) = SceneItem(
	projectDef = fakeProjectDef(),
	type = type,
	id = id,
	name = name,
	order = id,
)

private fun fakeSceneSummary(): SceneSummary {
	val tree = Tree<SceneItem>()
	val root = TreeNode(fakeScene(0, 0, SceneItem.Type.Root))
	val one = TreeNode(fakeScene(1, 0))
	root.addChild(one)
	tree.setRoot(root)

	return SceneSummary(
		tree.toImmutableTree(),
		persistentSetOf()
	)
}

@OptIn(ExperimentalFoundationApi::class)
@Preview
@Composable
fun SceneItemPreview() {
	KoinApplicationPreview {
	Padded {
	Column(modifier = Modifier.padding(Ui.Padding.L)) {
		SceneItem(
			scene = fakeScene(0, 0),
			draggable = Modifier,
			depth = 1,
			hasDirtyBuffer = false,
			isSelected = false,
			demonstrateSwipe = false,
			isChapter = false,
			onSceneSelected = {},
			onSceneDeleteRequest = {},
			onSceneRenameRequest = {},
			onSceneArchiveRequest = {},
			onSceneMoveRequest = {},
		)
		SceneItem(
			scene = fakeScene(1, 1),
			draggable = Modifier,
			depth = 1,
			hasDirtyBuffer = true,
			isSelected = false,
			demonstrateSwipe = false,
			isChapter = false,
			onSceneSelected = {},
			onSceneDeleteRequest = {},
			onSceneRenameRequest = {},
			onSceneArchiveRequest = {},
			onSceneMoveRequest = {},
		)
		SceneItem(
			scene = fakeScene(2, 2),
			draggable = Modifier,
			depth = 1,
			hasDirtyBuffer = false,
			isSelected = true,
			demonstrateSwipe = false,
			isChapter = false,
			onSceneSelected = {},
			onSceneDeleteRequest = {},
			onSceneRenameRequest = {},
			onSceneArchiveRequest = {},
			onSceneMoveRequest = {},
		)
	}
	}
	}
}

private fun fakeScene(
	id: Int,
	order: Int,
	type: SceneItem.Type = SceneItem.Type.Scene,
) = SceneItem(
	projectDef = fakeProjectDef(),
	type = type,
	id = id,
	name = "Test Scene $id",
	order = order
)

private fun fakeComponent(state: SceneList.State) = object : SceneList {
	override val state: Value<SceneList.State> = MutableValue(state)
	override fun onSceneSelected(sceneDef: SceneItem) {}
	override suspend fun moveScene(moveRequest: MoveRequest) {}
	override fun loadScenes() {}
	override suspend fun createScene(parent: SceneItem?, sceneName: String) {}
	override suspend fun createGroup(parent: SceneItem?, groupName: String) {}
	override fun beginCreate(kind: SceneList.CreateRequest.Kind, parent: SceneItem?) {}
	override fun dismissCreate() {}
	override suspend fun deleteScene(scene: SceneItem) {}
	override suspend fun renameScene(scene: SceneItem, newName: String): Boolean = false
	override fun onSceneListUpdate(scenes: SceneSummary) {}
	override fun onSceneBufferUpdate(sceneBuffer: SceneBuffer) {}
	override fun showOutlineOverview() {}
	override fun showReaderMode() {}
	override suspend fun archiveScene(scene: SceneItem) {}
	override suspend fun unarchiveScene(scene: SceneItem) {}
	override fun showArchivedScenes() {}
	override fun dismissArchivedDialog() {}
	override fun dismissHint(hint: NuxHint) {}
	override fun turnOffTips() {}
}
/** New scene inside a group, over a scrim; the card directly, since the animated dialog can't settle in a preview. */
@Composable
fun CreateSceneDialogScrimPreview() {
	PreviewScrim {
		CreateDialogCard(
			title = Res.string.scene_list_create_scene_dialog_title.get(),
			parentName = "Chapter IV",
			name = "The Lobster Quadrille",
			onNameChange = {},
			error = null,
			confirmEnabled = true,
			onConfirm = {},
			onCancel = {},
		)
	}
}

/** Renaming a chapter, over a scrim. */
@Composable
fun SceneRenameDialogScrimPreview() {
	PreviewScrim {
		SceneRenameCard(
			kind = SceneKind.Chapter,
			name = "Down the Rabbit-Hole",
			onNameChange = {},
			error = null,
			confirmEnabled = true,
			onConfirm = {},
			onCancel = {},
		)
	}
}
