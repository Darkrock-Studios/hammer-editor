package com.darkrockstudios.apps.hammer.common.sceneeditor

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.SceneItem
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditorUi

@Preview
@Composable
fun SceneEditorUiPreview() {
	val component = fakeComponent()
	SceneEditorUi(component)
}

private fun fakeSceneItem() = SceneItem(
	projectDef = fakeProjectDef(),
	type = SceneItem.Type.Scene,
	id = 0,
	name = "Test Scene",
	order = 0
)

private fun fakeProjectDef(): ProjectDef = ProjectDef(
	name = "Test",
	path = HPath(
		name = "Test",
		path = "/",
		isAbsolute = true
	)
)

private fun fakeComponent() = object : SceneEditor {
	override val state: Value<SceneEditor.State>
		get() = MutableValue(
			SceneEditor.State(
				sceneItem = fakeSceneItem(),
				isSavingDraft = false,
				isEditingName = false
			)
		)
	override var lastDiscarded = MutableValue(0L)

	override fun addEditorMenu() {}
	override fun removeEditorMenu() {}
	override fun loadSceneContent() {}
	override fun storeSceneContent() = true
	override fun onContentChanged(content: PlatformRichText) {}
	override fun beginSceneNameEdit() {}
	override fun endSceneNameEdit() {}
	override fun changeSceneName(newName: String) {}
	override fun beginSaveDraft() {}
	override fun endSaveDraft() {}
	override fun saveDraft(draftName: String) = true
}