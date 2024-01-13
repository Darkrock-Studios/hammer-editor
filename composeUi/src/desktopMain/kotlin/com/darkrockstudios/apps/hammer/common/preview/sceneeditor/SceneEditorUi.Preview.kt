package com.darkrockstudios.apps.hammer.common.preview.sceneeditor

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.darkrockstudios.apps.hammer.common.components.ToastMessage
import com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.SceneEditor
import com.darkrockstudios.apps.hammer.common.components.projecteditor.sceneeditor.scenemetadata.SceneMetadataPanel
import com.darkrockstudios.apps.hammer.common.compose.rememberRootSnackbarHostState
import com.darkrockstudios.apps.hammer.common.data.Msg
import com.darkrockstudios.apps.hammer.common.data.PlatformRichText
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.preview.fakeSceneItem
import com.darkrockstudios.apps.hammer.common.storyeditor.sceneeditor.SceneEditorUi
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

@Preview
@Composable
fun SceneEditorUiPreview() {
	val component = fakeComponent()
	val rootSnackbar = rememberRootSnackbarHostState()
	SceneEditorUi(component, rootSnackbar)
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
	override val sceneMetadataComponent = object : SceneMetadataPanel {
		override val state = MutableValue(SceneMetadataPanel.State(fakeSceneItem()))
		override fun updateOutline(text: String) {}
		override fun updateNotes(text: String) {}
	}

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
	override val toast = MutableSharedFlow<ToastMessage>()
	override fun showToast(scope: CoroutineScope, message: StringResource, vararg params: Any) {}
	override fun showToast(scope: CoroutineScope, message: Msg) {}
	override suspend fun showToast(message: StringResource, vararg params: Any) {}
	override suspend fun showToast(message: Msg) {}
	override fun closeEditor() {}
	override fun beginDelete() {}
	override fun endDelete() {}
	override fun doDelete() {}
	override fun toggleMetadataVisibility() {}
	override fun decreaseTextSize() {}
	override fun increaseTextSize() {}
	override fun resetTextSize() {}
}