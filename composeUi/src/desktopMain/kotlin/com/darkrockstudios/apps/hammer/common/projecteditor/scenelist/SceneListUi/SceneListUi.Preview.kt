package com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneListUi

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.MoveRequest
import com.darkrockstudios.apps.hammer.common.data.SceneBuffer
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.data.SceneSummary
import com.darkrockstudios.apps.hammer.common.preview.fakeProjectDef
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneItemUi
import com.darkrockstudios.apps.hammer.common.projecteditor.scenelist.SceneList

/*
@Preview
@Composable
fun SceneListUiPreview() {
	val component = fakeComponent(
		SceneList.State(
			projectDef = fakeProjectDef(),
			sceneSummary = fakeSceneSummary()
		)
	)
	SceneListUi(component)
}


private fun fakeSceneSummary() = SceneSummary(
	sceneTree = ImmutableTree(
		root = TreeValue(fakeScene(0, 0)),
		totalChildren = 3
	),
	hasDirtyBuffer = emptySet()
)

private fun fakeNode(id: Int, order: Int): TreeValue<SceneItem> {
	return TreeValue(
		value = fakeScene(id, order),
		index = 0,

	)
}
*/

@Preview
@Composable
private fun SceneItemPreview() {
	Column {
		SceneItemUi(
			scene = fakeScene(0, 0),
			draggable = Modifier,
			depth = 0,
			hasDirtyBuffer = false,
			isSelected = false,
			onSceneSelected = {},
			onSceneAltClick = {}
		)
		SceneItemUi(
			scene = fakeScene(1, 1),
			draggable = Modifier,
			depth = 0,
			hasDirtyBuffer = true,
			isSelected = false,
			onSceneSelected = {},
			onSceneAltClick = {}
		)
		SceneItemUi(
			scene = fakeScene(2, 2),
			draggable = Modifier,
			depth = 0,
			hasDirtyBuffer = false,
			isSelected = true,
			onSceneSelected = {},
			onSceneAltClick = {}
		)
	}
}

private fun fakeScene(id: Int, order: Int) = SceneItem(
	projectDef = fakeProjectDef(),
	type = SceneItem.Type.Scene,
	id = id,
	name = "Test Scene $id",
	order = order
)

private fun fakeGroup(id: Int, order: Int) = SceneItem(
	projectDef = fakeProjectDef(),
	type = SceneItem.Type.Group,
	id = id,
	name = "Test Scene $id",
	order = order
)


private fun fakeComponent(state: SceneList.State) = object : SceneList {
	override val state: Value<SceneList.State> = MutableValue(state)
	override fun onSceneSelected(sceneDef: SceneItem) {}
	override fun moveScene(moveRequest: MoveRequest) {}
	override fun loadScenes() {}
	override fun createScene(sceneName: String) {}
	override fun createGroup(groupName: String) {}
	override fun deleteScene(scene: SceneItem) {}
	override fun onSceneListUpdate(scenes: SceneSummary) {}
	override fun onSceneBufferUpdate(sceneBuffer: SceneBuffer) {}
}