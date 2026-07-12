package com.darkrockstudios.apps.hammer.common.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.Padded
import com.darkrockstudios.apps.hammer.common.components.storyeditor.scenelist.SceneList
import com.darkrockstudios.apps.hammer.common.compose.Ui
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.data.tree.Tree
import com.darkrockstudios.apps.hammer.common.data.tree.TreeNode
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.scenelist.SceneListUi
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
			val component = fakeComponent(
				SceneList.State(
					projectDef = fakeProjectDef(),
					sceneSummary = fakeSceneSummary()
				)
			)
			SceneListUi(component, snackbarHostState)
		}
	}
}

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
			shouldNux = false,
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
			shouldNux = false,
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
			shouldNux = false,
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
	override suspend fun deleteScene(scene: SceneItem) {}
	override suspend fun renameScene(scene: SceneItem, newName: String): Boolean = false
	override fun onSceneListUpdate(scenes: SceneSummary) {}
	override fun onSceneBufferUpdate(sceneBuffer: SceneBuffer) {}
	override fun showOutlineOverview() {}
	override suspend fun archiveScene(scene: SceneItem) {}
	override suspend fun unarchiveScene(scene: SceneItem) {}
	override fun showArchivedScenes() {}
	override fun dismissArchivedDialog() {}
}