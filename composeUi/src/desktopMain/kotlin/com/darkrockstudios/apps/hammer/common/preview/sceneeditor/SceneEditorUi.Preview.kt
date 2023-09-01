package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.preview.fakeSceneItem
import com.darkrockstudios.apps.hammer.common.projecteditor.sceneeditor.SceneEditorUi

@Preview
@Composable
fun SceneEditorUiPreview() {
	val component = fakeComponent()
	SceneEditorUi(component)
}

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
	override var lastForceUpdate = MutableValue(0L)

	override fun addEditorMenu() {}
	override fun removeEditorMenu() {}
	override fun loadSceneContent() {}
	override suspend fun storeSceneContent() = true
	override fun onContentChanged(content: PlatformRichText) {}
	override fun beginSceneNameEdit() {}
	override fun endSceneNameEdit() {}
	override suspend fun changeSceneName(newName: String) {}
	override fun beginSaveDraft() {}
	override fun endSaveDraft() {}
	override suspend fun saveDraft(draftName: String) = true
	override fun closeEditor() {}
}